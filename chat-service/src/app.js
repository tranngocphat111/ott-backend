const express = require("express");
const http = require("http");
const crypto = require("crypto");
const { Server } = require("socket.io");
const cors = require("cors");

const connectDB = require("./config/db");
const apiRoutes = require("./routes/api");
const messageRoutes = require("./routes/messageRoutes");
const messageEventsHandler = require("./events/messageEvents");
const ParticipantService = require("./services/participantService");
const aiRoutes = require("./routes/aiRoutes");
const MessageService = require("./services/messageService");
const { initAllConsumers } = require("./consumers");
const Conversation = require("./models/Conversation");
const Message = require("./models/Message");
const User = require("./models/User");
const livekitService = require("./services/livekitService");
const { activeCalls } = require("./services/callStateService");
const presenceService = require("./services/presenceService");
const {
  publishMessageDelivered,
  publishMessageSeen,
} = require("./events/chatEvents");

const envEnabled = (name, defaultValue = true) => {
  const raw = process.env[name];
  if (raw === undefined) return defaultValue;
  return String(raw).toLowerCase() !== "false";
};

const chatReceiptQueueEnabled = envEnabled("CHAT_RECEIPT_QUEUE_ENABLED", false);

connectDB();
const app = express();
const server = http.createServer(app);

const normalizeOrigin = (origin) => String(origin || "").trim().replace(/\/+$/, "");
const splitOrigins = (...values) =>
  values
    .flatMap((value) => String(value || "").split(","))
    .map(normalizeOrigin)
    .filter(Boolean);
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const allowedOrigins = Array.from(new Set(splitOrigins(
  process.env.CHAT_ALLOWED_ORIGINS,
  process.env.CORS_ALLOWED_ORIGINS,
  process.env.FRONTEND_URL,
  process.env.FRONTEND_URL_ALT,
  process.env.FRONTEND_URL_DEPLOYED,
  "http://localhost:5173",
  "http://127.0.0.1:5173",
  "http://localhost:3000",
  "https://*.vercel.app",
)));

const isOriginAllowed = (origin) => {
  const normalizedOrigin = normalizeOrigin(origin);
  if (!normalizedOrigin) return true;

  return allowedOrigins.some((allowedOrigin) => {
    if (allowedOrigin === "*") return true;
    if (allowedOrigin === normalizedOrigin) return true;
    if (!allowedOrigin.includes("*")) return false;

    const pattern = `^${escapeRegExp(allowedOrigin).replace(/\\\*/g, ".*")}$`;
    return new RegExp(pattern).test(normalizedOrigin);
  });
};

const corsOrigin = (origin, callback) => {
  callback(null, isOriginAllowed(origin));
};

const corsOptions = {
  origin: corsOrigin,
  methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"],
  allowedHeaders: ["Content-Type", "Authorization", "Accept", "X-Requested-With"],
  credentials: false,
};

app.use(cors(corsOptions));
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ limit: "10mb", extended: true }));

const io = new Server(server, {
  cors: {
    origin: corsOrigin,
    methods: corsOptions.methods,
    allowedHeaders: corsOptions.allowedHeaders,
    credentials: false,
  },
});

// Initialize consumers with io
initAllConsumers(io);

// ========== MESSAGE EVENTS HANDLER ==========
messageEventsHandler(io);
// ==========================================

// In-memory call state by conversationId.
// Note: This is suitable for single-node deployments.
const NO_ANSWER_TIMEOUT_MS = 30000;
const CALL_RECONNECT_GRACE_MS = 8000;
const CALL_OUTCOME_DEDUPE_TTL_MS = 15000;
const CALL_OUTCOME_RECENT_LOOKBACK_MS = 15000;
const MAX_GROUP_CALL_PARTICIPANTS = 8;
const CALL_OUTCOME_TYPES = [
  "call_end",
  "call_missed",
  "call_cancel",
  "call_no_answer",
];
const recentCallOutcomeKeys = new Map();

const normalizeId = (value) => String(value || "").trim();

const createCallId = () => {
  if (typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const normalizeCallType = (callType) => {
  return callType === "voice" ? "voice" : "video";
};

const formatCallDuration = (seconds) => {
  const safeSeconds = Math.max(0, Number(seconds || 0));
  const minutes = Math.floor(safeSeconds / 60);
  const secs = safeSeconds % 60;

  if (minutes > 0) {
    return `${minutes} phút ${secs} giây`;
  }

  return `${secs} giây`;
};

const getCallRoomName = (callState) => {
  if (!callState) return "";
  return `call:${callState.callId || callState.conversationId}`;
};

const isSameCall = (callState, callId) => {
  if (!callState) return false;
  const normalizedCallId = normalizeId(callId);
  if (!normalizedCallId) return true;
  return normalizeId(callState.callId) === normalizedCallId;
};

const clearNoAnswerTimer = (callState) => {
  if (callState?.noAnswerTimer) {
    clearTimeout(callState.noAnswerTimer);
    callState.noAnswerTimer = null;
  }
};

const clearParticipantDisconnectTimer = (callState, userId) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId || !callState?.disconnectTimers) return;

  const timer = callState.disconnectTimers.get(normalizedUserId);
  if (!timer) return;

  clearTimeout(timer);
  callState.disconnectTimers.delete(normalizedUserId);
};

const ensureCallDeviceState = (callState) => {
  if (!callState) return null;
  if (!callState.activeParticipantSockets) {
    callState.activeParticipantSockets = new Map();
  }
  return callState.activeParticipantSockets;
};

const isSocketConnected = (socketId) => {
  const normalizedSocketId = normalizeId(socketId);
  return !!normalizedSocketId && io.sockets.sockets.has(normalizedSocketId);
};

const getActiveParticipantSocketId = (callState, userId) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return "";

  const socketMap = ensureCallDeviceState(callState);
  return normalizeId(socketMap?.get(normalizedUserId));
};

const setActiveParticipantSocket = (callState, userId, socketId) => {
  const normalizedUserId = normalizeId(userId);
  const normalizedSocketId = normalizeId(socketId);
  if (!normalizedUserId || !normalizedSocketId) return;

  ensureCallDeviceState(callState)?.set(normalizedUserId, normalizedSocketId);
};

const clearActiveParticipantSocket = (callState, userId) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return;

  ensureCallDeviceState(callState)?.delete(normalizedUserId);
};

const isParticipantActiveOnAnotherDevice = (callState, userId, socketId) => {
  const activeSocketId = getActiveParticipantSocketId(callState, userId);
  if (!activeSocketId) return false;
  if (activeSocketId === normalizeId(socketId)) return false;

  // If the recorded call socket is gone, let another device take over.
  return isSocketConnected(activeSocketId);
};

const buildAnsweredElsewherePayload = (
  callState,
  userId,
  reason = "answered_elsewhere",
) => ({
  conversationId: normalizeId(callState?.conversationId),
  callId: normalizeId(callState?.callId),
  userId: normalizeId(userId),
  acceptedSocketId: getActiveParticipantSocketId(callState, userId),
  isGroup: !!callState?.isGroup,
  reason,
});

const emitAnsweredElsewhereToCurrentSocket = (
  socket,
  callState,
  userId,
  reason = "already_joined_elsewhere",
) => {
  socket.emit(
    "cuoc_goi_da_nhan_o_thiet_bi_khac",
    buildAnsweredElsewherePayload(callState, userId, reason),
  );
};

const notifyOtherDevicesAnsweredElsewhere = (
  socket,
  callState,
  userId,
) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return;

  socket.to(`user:${normalizedUserId}`).emit(
    "cuoc_goi_da_nhan_o_thiet_bi_khac",
    buildAnsweredElsewherePayload(callState, normalizedUserId),
  );
};

const buildGroupCallUpdatePayload = (callState) => ({
  conversationId: normalizeId(callState.conversationId),
  callId: normalizeId(callState.callId),
  callType: normalizeCallType(callState.callType),
  isCalling: callState.participants.size > 0,
  participants: Array.from(callState.participants),
  participantCount: Math.min(callState.participants.size, MAX_GROUP_CALL_PARTICIPANTS),
  maxParticipants: MAX_GROUP_CALL_PARTICIPANTS,
});

const emitGroupCallUpdate = (callState) => {
  if (!callState?.isGroup) return;

  const updatePayload = buildGroupCallUpdatePayload(callState);

  io.to(normalizeId(callState.conversationId)).emit(
    "cap_nhat_trang_thai_goi_nhom",
    updatePayload,
  );
  io.to(`conversation:${callState.conversationId}`).emit(
    "cap_nhat_trang_thai_goi_nhom",
    updatePayload,
  );

  if (callState.memberIds) {
    callState.memberIds.forEach((uid) => {
      io.to(`user:${uid}`).emit("cap_nhat_trang_thai_goi_nhom", updatePayload);
    });
  }
};

const pruneRecentCallOutcomeKeys = () => {
  const now = Date.now();
  for (const [key, expiresAt] of recentCallOutcomeKeys.entries()) {
    if (expiresAt <= now) {
      recentCallOutcomeKeys.delete(key);
    }
  }
};

const claimCallOutcome = (key) => {
  if (!key) return true;

  pruneRecentCallOutcomeKeys();
  if (recentCallOutcomeKeys.has(key)) {
    return false;
  }

  recentCallOutcomeKeys.set(key, Date.now() + CALL_OUTCOME_DEDUPE_TTL_MS);
  return true;
};

const hasCallBeenAnswered = (callState) => {
  return Boolean(callState?.answeredAt || callState?.hadMultipleParticipants);
};

const isUserBusyInAnotherCall = (userId, conversationId) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return false;

  for (const [activeConversationId, callState] of activeCalls.entries()) {
    if (activeConversationId === conversationId) continue;
    if (callState.status === "ended") continue;
    if (callState.participants.has(normalizedUserId)) {
      return true;
    }
  }

  return false;
};

const endCallRoom = async (
  conversationId,
  endedBy = null,
  expectedCallId = null,
  reason = "ended",
) => {
  const callState = activeCalls.get(conversationId);
  if (!callState) return;
  if (!isSameCall(callState, expectedCallId)) return;

  clearNoAnswerTimer(callState);
  if (callState.disconnectTimers) {
    callState.disconnectTimers.forEach((timer) => clearTimeout(timer));
    callState.disconnectTimers.clear();
  }

  const payload = {
    conversationId: String(conversationId),
    callId: normalizeId(callState.callId),
    endedBy: endedBy ? String(endedBy) : null,
    reason,
  };

  console.log(`[CALL] endCallRoom: conversationId=${payload.conversationId}, callId=${payload.callId}, endedBy=${payload.endedBy}, reason=${reason}`);

  io.to(getCallRoomName(callState)).emit("ket_thuc_phong_goi", payload);

  // Luôn phát tín hiệu đến memberIds từ cache để đảm bảo tốc độ và sự tin cậy
  if (callState.memberIds) {
    callState.memberIds.forEach((uid) => {
      io.to(`user:${uid}`).emit("ket_thuc_phong_goi", payload);
    });
  }

  // Dự phòng: Phát tín hiệu đến toàn bộ thành viên theo DB
  try {
    const participants = await ParticipantService.getParticipants(conversationId);
    participants.forEach((participant) => {
      io.to(`user:${participant.user_id}`).emit("ket_thuc_phong_goi", payload);
    });
  } catch (error) {
    console.error("Loi khi lay participants de endCallRoom:", error);
    // Fallback: dung memberIds tu cache neu DB loi
    if (callState.memberIds) {
      callState.memberIds.forEach((uid) => {
        io.to(`user:${uid}`).emit("ket_thuc_phong_goi", payload);
      });
    }
  }

  io.in(getCallRoomName(callState)).socketsLeave(getCallRoomName(callState));
  if (callState.isGroup) {
    callState.participants.clear();
    emitGroupCallUpdate(callState);
  }

  activeCalls.delete(conversationId);
};

const emitMessageToConversationParticipants = async (
  conversationId,
  message,
) => {
  if (!conversationId || !message) return;

  const participants = await ParticipantService.getParticipants(conversationId);
  participants.forEach((participant) => {
    io.to(`user:${participant.user_id}`).emit("tin_nhan", message);
  });
};

const createCallNotificationMessage = async ({
  conversationId,
  senderId,
  type,
  content,
  systemMeta,
}) => {
  if (!conversationId || !senderId || !type || !content) return;

  try {
    console.log(`[CALL] create notification: conversationId=${conversationId}, senderId=${senderId}, type=${type}, content=${content}`);
    const message = await MessageService.sendMessage({
      conversationId,
      senderId,
      content,
      type,
      systemMeta,
    });

    console.log(`[CALL] notification created: conversationId=${conversationId}, msgId=${message?.msg_id || ""}, type=${type}`);
    await emitMessageToConversationParticipants(conversationId, message);
    return message;
  } catch (error) {
    console.error("Khong the tao thong bao cuoc goi:", error.message);
    return null;
  }
};

const getUserDisplayName = async (userId) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return "Ai đó";

  try {
    const user = await User.findOne({ user_id: normalizedUserId })
      .select("name")
      .lean();
    return String(user?.name || "").trim() || "Ai đó";
  } catch (error) {
    console.error("Khong the lay ten user cho thong bao cuoc goi:", error.message);
    return "Ai đó";
  }
};

const getCallMemberDisplayName = (member, userId) => {
  const fallbackId = normalizeId(userId || member?.user_id);
  const fallback = fallbackId ? `User ${fallbackId.slice(-4)}` : "Người dùng";

  return String(
    member?.nickname ||
      member?.user?.name ||
      fallback,
  ).trim();
};

const getCallParticipantDetails = async (conversationId, participantIds = []) => {
  const normalizedIds = Array.from(
    new Set(participantIds.map((id) => normalizeId(id)).filter(Boolean)),
  );

  if (!conversationId || normalizedIds.length === 0) {
    return [];
  }

  try {
    const members = await ParticipantService.getConversationMembers(conversationId);
    const memberById = new Map(
      (members || [])
        .map((member) => [normalizeId(member?.user_id), member])
        .filter(([userId]) => Boolean(userId)),
    );

    return normalizedIds.map((userId) => {
      const member = memberById.get(userId);
      return {
        userId,
        name: getCallMemberDisplayName(member, userId),
        avatar: String(member?.user?.avatar || "").trim(),
      };
    });
  } catch (error) {
    console.error("Khong the lay metadata thanh vien cuoc goi:", error.message);
    return normalizedIds.map((userId) => ({
      userId,
      name: `User ${userId.slice(-4)}`,
      avatar: "",
    }));
  }
};

const getCallParticipantProfile = async (conversationId, userId) => {
  const [profile] = await getCallParticipantDetails(conversationId, [userId]);
  return profile || {
    userId: normalizeId(userId),
    name: `User ${normalizeId(userId).slice(-4)}`,
    avatar: "",
  };
};

const buildCallParticipantPayload = async (conversationId, callState) => {
  const participants = Array.from(callState?.participants || []);
  return {
    participants,
    participantDetails: await getCallParticipantDetails(conversationId, participants),
  };
};

const generateCallLiveKitToken = async (callState, conversationId, userId) => {
  const normalizedUserId = normalizeId(userId);
  const profile = await getCallParticipantProfile(conversationId, normalizedUserId);

  return livekitService.generateToken(callState.mediaRoomName, normalizedUserId, {
    name: profile.name,
    metadata: JSON.stringify({
      userId: profile.userId,
      name: profile.name,
      avatar: profile.avatar || "",
    }),
  });
};

const createCallJoinNotificationMessage = async ({
  conversationId,
  userId,
  callState,
}) => {
  if (!conversationId || !userId || !callState?.isGroup) return null;

  const displayName = await getUserDisplayName(userId);
  return createCallNotificationMessage({
    conversationId,
    senderId: userId,
    type: "call_join",
    content: `${displayName} đã tham gia cuộc gọi`,
    systemMeta: {
      callId: normalizeId(callState.callId) || null,
      callType: callState.callType || "video",
      action: "joined_call",
      userId: normalizeId(userId),
      joinedAt: new Date().toISOString(),
    },
  });
};

const getRecentCallOutcomeMessage = async (conversationId, callId = null) => {
  if (!conversationId) return null;

  const since = new Date(Date.now() - CALL_OUTCOME_RECENT_LOOKBACK_MS);
  return Message.findOne({
    conversation_id: conversationId,
    type: { $in: CALL_OUTCOME_TYPES },
    is_deleted: { $ne: true },
    ...(callId ? { "system_meta.callId": normalizeId(callId) } : {}),
    createdAt: { $gte: since },
  })
    .sort({ createdAt: -1 })
    .select("msg_id type createdAt")
    .lean();
};

const emitFallbackCallOutcomeForMissingState = async ({
  conversationId,
  userId,
  callId,
  callType,
  wasConnected,
  durationSeconds,
}) => {
  if (!conversationId || !userId) return false;

  const normalizedCallId = normalizeId(callId);
  const dedupeKey = `missing-state:${conversationId}:${normalizedCallId || userId}`;
  if (!claimCallOutcome(dedupeKey)) {
    return false;
  }

  const recentCallMessage = await getRecentCallOutcomeMessage(
    conversationId,
    normalizedCallId,
  );
  if (recentCallMessage) {
    console.warn(
      `[CALL] fallback skipped: recent ${recentCallMessage.type} already exists for conversationId=${conversationId}, msgId=${recentCallMessage.msg_id || ""}`,
    );
    return false;
  }

  const normalizedCallType = normalizeCallType(callType);
  const safeDurationSeconds = Number.isFinite(Number(durationSeconds))
    ? Math.max(0, Math.floor(Number(durationSeconds)))
    : 0;
  const isCompleted = Boolean(wasConnected) || safeDurationSeconds > 0;
  const messageType = isCompleted ? "call_end" : "call_missed";
  const content = isCompleted
    ? `Cuộc gọi ${normalizedCallType === "video" ? "video" : "thoại"} - ${formatCallDuration(safeDurationSeconds)}`
    : `Cuộc gọi ${normalizedCallType === "video" ? "video" : "thoại"} nhỡ`;

  console.warn(
    `[CALL] fallback notification: conversationId=${conversationId}, userId=${userId}, type=${messageType}, callType=${normalizedCallType}, durationSeconds=${safeDurationSeconds}`,
  );

  const message = await createCallNotificationMessage({
    conversationId,
    senderId: userId,
    type: messageType,
    content,
    systemMeta: {
      callId: normalizedCallId || null,
      callType: normalizedCallType,
      outcome: isCompleted ? "completed" : "missed",
      reason: "missing_state_fallback",
      durationSeconds: safeDurationSeconds,
      endedAt: new Date().toISOString(),
    },
  });

  return Boolean(message);
};

const scheduleNoAnswerTimeout = ({ conversationId, callerId, callType }) => {
  const callState = activeCalls.get(conversationId);
  if (!callState) return;

  clearNoAnswerTimer(callState);
  const callId = callState.callId;

  callState.noAnswerTimer = setTimeout(async () => {
    const currentCallState = activeCalls.get(conversationId);
    if (!currentCallState || !isSameCall(currentCallState, callId)) return;

    console.log(`[CALL] No-answer timeout (30s) reached for conversation ${conversationId}, callId=${callId}.`);

    // Tự động "từ chối" cho những người chưa bắt máy sau 30s
    const ringingMemberIds =
      currentCallState.ringingMemberIds instanceof Set
        ? currentCallState.ringingMemberIds
        : currentCallState.memberIds;
    if (ringingMemberIds) {
      ringingMemberIds.forEach(uid => {
        if (!currentCallState.participants.has(uid)) {
          // Gửi tín hiệu để client tự động đóng modal/dừng đổ chuông
          io.to(`user:${uid}`).emit("ket_thuc_phong_goi", {
            conversationId,
            callId,
            reason: "timeout",
            message: "Cuộc gọi đã kết thúc do không trả lời"
          });
          console.log(`[CALL] Auto-rejecting for user ${uid} (timeout)`);
        }
      });
    }

    currentCallState.noAnswerTimer = null;

    if (currentCallState.isGroup) {
      if (currentCallState.participants.size === 0) {
        await finishCall({
          conversationId,
          callId,
          endedBy: callerId,
          outcome: "no_answer",
          reason: "timeout",
        });
      } else {
        console.log(`[CALL] Group room ${conversationId} remains active for ${currentCallState.participants.size} participant(s).`);
      }
      return;
    }

    // 1:1: nếu hết thời gian mà vẫn chỉ có caller, kết thúc bằng no-answer.
    if (currentCallState.participants.size <= 1) {
      await finishCall({
        conversationId,
        callId,
        endedBy: callerId,
        outcome: "no_answer",
        reason: "timeout",
      });

      console.log(`[CALL] Room ${conversationId} closed due to no response.`);
    } else {
      console.log(`[CALL] Room ${conversationId} remains active for ${currentCallState.participants.size} participants.`);
    }
  }, NO_ANSWER_TIMEOUT_MS);
};

const emitCallOutcomeMessage = async ({
  conversationId,
  senderId,
  callId,
  callType,
  outcome,
  answeredAt,
  endedAt = new Date().toISOString(),
  durationSeconds,
  dedupeKey,
  reason,
}) => {
  if (!conversationId || !senderId) return null;
  if (!claimCallOutcome(dedupeKey)) return;

  const normalizedCallId = normalizeId(callId);
  if (normalizedCallId) {
    const existingMessage = await Message.findOne({
      conversation_id: conversationId,
      type: { $in: CALL_OUTCOME_TYPES },
      "system_meta.callId": normalizedCallId,
      is_deleted: { $ne: true },
    })
      .sort({ createdAt: -1 })
      .select("msg_id type")
      .lean();

    if (existingMessage) {
      console.warn(
        `[CALL] outcome skipped: message already exists for callId=${normalizedCallId}, msgId=${existingMessage.msg_id || ""}`,
      );
      return existingMessage;
    }
  }

  const normalizedCallType = normalizeCallType(callType);
  const baseMeta = {
    callId: normalizedCallId || null,
    callType: normalizedCallType,
    outcome,
    reason: reason || outcome,
    answeredAt: answeredAt || null,
    endedAt,
  };

  if (outcome === "completed") {
    const startAt = answeredAt ? new Date(answeredAt).getTime() : null;
    const endAt = new Date(endedAt).getTime();
    const safeDurationSeconds = Number.isFinite(Number(durationSeconds))
      ? Math.max(0, Math.floor(Number(durationSeconds)))
      : startAt
      ? Math.max(0, Math.floor((endAt - startAt) / 1000))
      : 0;

    return createCallNotificationMessage({
      conversationId,
      senderId,
      type: "call_end",
      content: `Cuộc gọi ${normalizedCallType === "video" ? "video" : "thoại"} - ${formatCallDuration(safeDurationSeconds)}`,
      systemMeta: {
        ...baseMeta,
        durationSeconds: safeDurationSeconds,
      },
    });
  }

  const messageType =
    outcome === "cancelled"
      ? "call_cancel"
      : outcome === "no_answer"
      ? "call_no_answer"
      : "call_missed";
  const suffix =
    outcome === "cancelled"
      ? "đã hủy"
      : outcome === "no_answer"
      ? "không trả lời"
      : "nhỡ";

  return createCallNotificationMessage({
    conversationId,
    senderId,
    type: messageType,
    content: `Cuộc gọi ${normalizedCallType === "video" ? "video" : "thoại"} ${suffix}`,
    systemMeta: baseMeta,
  });
};

const emitCallOutcomeForState = async ({
  conversationId,
  senderId,
  callState,
  outcome,
  durationSeconds,
  reason,
}) => {
  if (!callState || callState.isOutcomeEmitted) return;

  callState.isOutcomeEmitted = true;
  console.log(
    `[CALL] outcome: conversationId=${conversationId}, callId=${callState.callId || ""}, outcome=${outcome}, answeredAt=${callState.answeredAt || ""}, hadMultipleParticipants=${!!callState.hadMultipleParticipants}, durationSeconds=${durationSeconds ?? ""}`,
  );
  await emitCallOutcomeMessage({
    conversationId,
    senderId: senderId || callState.initiatorId || "",
    callId: callState.callId,
    callType: callState.callType || "video",
    outcome,
    answeredAt: callState.answeredAt,
    endedAt: callState.endedAt || new Date().toISOString(),
    durationSeconds,
    dedupeKey: callState.outcomeKey,
    reason,
  });
};

const resolveUnansweredOutcome = (callState, endedBy, fallback = "missed") => {
  if (fallback === "no_answer" || fallback === "cancelled") return fallback;

  const normalizedEndedBy = normalizeId(endedBy);
  if (
    normalizedEndedBy &&
    normalizeId(callState?.initiatorId) === normalizedEndedBy
  ) {
    return "cancelled";
  }

  return fallback;
};

const finishCall = async ({
  conversationId,
  callId,
  endedBy = null,
  outcome,
  reason,
  durationSeconds,
}) => {
  const callState = activeCalls.get(conversationId);
  if (!callState || !isSameCall(callState, callId)) {
    return { ok: false, reason: "call_not_found" };
  }

  if (callState.status === "ended") {
    return {
      ok: true,
      callId: callState.callId,
      reason: "already_ended",
      outcome: callState.outcome || outcome,
    };
  }

  const endedAt = new Date().toISOString();
  callState.status = "ended";
  callState.endedAt = endedAt;
  callState.endedBy = normalizeId(endedBy) || null;
  callState.endReason = reason || outcome || "ended";

  clearNoAnswerTimer(callState);

  const wasAnswered = hasCallBeenAnswered(callState);
  const finalOutcome =
    outcome ||
    (wasAnswered
      ? "completed"
      : resolveUnansweredOutcome(callState, endedBy, "missed"));
  callState.outcome = finalOutcome;

  await emitCallOutcomeForState({
    conversationId,
    senderId: callState.initiatorId || endedBy || "",
    callState,
    outcome: finalOutcome,
    durationSeconds,
    reason: reason || finalOutcome,
  });

  await endCallRoom(
    conversationId,
    endedBy,
    callState.callId,
    reason || finalOutcome,
  );

  return {
    ok: true,
    callId: callState.callId,
    outcome: finalOutcome,
    reason: reason || finalOutcome,
  };
};

const maybeCloseCallWhenOnlyOneLeft = async (
  conversationId,
  endedBy = null,
  callId = null,
) => {
  const callState = activeCalls.get(conversationId);
  if (!callState || !isSameCall(callState, callId)) return;

  const shouldClose = callState.isGroup
    ? callState.participants.size === 0
    : callState.participants.size <= 1;

  if (!shouldClose) return;

  const wasAnswered = hasCallBeenAnswered(callState);
  const outcome = wasAnswered
    ? "completed"
    : resolveUnansweredOutcome(callState, endedBy, "cancelled");

  await finishCall({
    conversationId,
    callId: callState.callId,
    endedBy,
    outcome,
    reason: outcome,
  });
};

const removeParticipantFromCall = async ({
  conversationId,
  userId,
  callId = null,
  socketToLeave = null,
  reason = "left",
}) => {
  const callState = activeCalls.get(conversationId);
  const normalizedUserId = normalizeId(userId);

  if (!callState || !normalizedUserId || !isSameCall(callState, callId)) {
    return { ok: false, reason: "call_not_found" };
  }

  clearParticipantDisconnectTimer(callState, normalizedUserId);

  if (socketToLeave && isParticipantActiveOnAnotherDevice(callState, normalizedUserId, socketToLeave.id)) {
    socketToLeave.leave(getCallRoomName(callState));
    emitAnsweredElsewhereToCurrentSocket(
      socketToLeave,
      callState,
      normalizedUserId,
      "stale_device_ignored",
    );
    return {
      ok: true,
      callId: callState.callId,
      participants: Array.from(callState.participants),
      reason: "stale_device_ignored",
    };
  }

  const wasParticipant = callState.participants.delete(normalizedUserId);
  if (!wasParticipant) {
    return {
      ok: true,
      callId: callState.callId,
      participants: Array.from(callState.participants),
      reason: "not_participant",
    };
  }

  clearActiveParticipantSocket(callState, normalizedUserId);

  if (socketToLeave) {
    socketToLeave.leave(getCallRoomName(callState));
  }

  const payload = {
    conversationId,
    callId: callState.callId,
    userId: normalizedUserId,
    participants: Array.from(callState.participants),
    reason,
  };

  io.to(getCallRoomName(callState)).emit("nguoi_dung_roi_goi", payload);
  emitGroupCallUpdate(callState);

  await maybeCloseCallWhenOnlyOneLeft(
    conversationId,
    normalizedUserId,
    callState.callId,
  );

  return { ok: true, callId: callState.callId, participants: payload.participants };
};

const ensureCallState = (conversationId, callType, memberIds = [], isGroup = false) => {
  if (!activeCalls.has(conversationId)) {
    const startedAt = new Date().toISOString();
    const callId = createCallId();
    const effectiveCallType = isGroup ? "video" : normalizeCallType(callType);
    activeCalls.set(conversationId, {
      conversationId,
      callId,
      mediaRoomName: `call-${callId}`,
      callType: effectiveCallType,
      status: "ringing",
      participants: new Set(),
      memberIds: new Set(memberIds.map((id) => normalizeId(id)).filter(Boolean)), // Lưu danh sách member để signaling cancel
      ringingMemberIds: new Set(),
      declinedMemberIds: new Set(),
      disconnectTimers: new Map(),
      activeParticipantSockets: new Map(),
      startedAt,
      outcomeKey: `${conversationId}:${callId}`,
      answeredAt: null,
      isOutcomeEmitted: false,
      isGroup: !!isGroup,
      hadMultipleParticipants: false,
      noAnswerTimer: null,
    });
  } else if (isGroup) {
    // Cập nhật trạng thái group nếu thông tin mới xác nhận là group
    const callState = activeCalls.get(conversationId);
    callState.isGroup = true;
    callState.callType = "video";
  }
  return activeCalls.get(conversationId);
};

const removeUserFromAllCalls = async (userId, reason = "disconnect") => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return;

  const removals = [];
  for (const [conversationId, callState] of activeCalls.entries()) {
    if (!callState.participants.has(normalizedUserId)) {
      continue;
    }

    removals.push(
      removeParticipantFromCall({
        conversationId,
        userId: normalizedUserId,
        callId: callState.callId,
        reason,
      }),
    );
  }

  if (removals.length > 0) {
    await Promise.allSettled(removals);
  }
};

const scheduleUserDisconnectFromAllCalls = (userId, socketId = null) => {
  const normalizedUserId = normalizeId(userId);
  if (!normalizedUserId) return;

  for (const [conversationId, callState] of activeCalls.entries()) {
    if (!callState.participants.has(normalizedUserId)) continue;
    const activeSocketId = getActiveParticipantSocketId(callState, normalizedUserId);
    if (socketId && activeSocketId && activeSocketId !== normalizeId(socketId)) continue;
    if (!callState.disconnectTimers) {
      callState.disconnectTimers = new Map();
    }
    if (callState.disconnectTimers.has(normalizedUserId)) continue;

    const callId = callState.callId;
    const timer = setTimeout(() => {
      const currentCallState = activeCalls.get(conversationId);
      if (
        !currentCallState ||
        !isSameCall(currentCallState, callId) ||
        !currentCallState.participants.has(normalizedUserId)
      ) {
        return;
      }

      currentCallState.disconnectTimers?.delete(normalizedUserId);
      void removeParticipantFromCall({
        conversationId,
        userId: normalizedUserId,
        callId,
        reason: "disconnect",
      });
    }, CALL_RECONNECT_GRACE_MS);

    callState.disconnectTimers.set(normalizedUserId, timer);
  }
};

app.use((req, res, next) => {
  req.io = io;
  next();
});

io.on("connection", (socket) => {
  console.log("User moi vua ket noi:", socket.id);

  socket.on("tham_gia_nhom", (conversationId) => {
    socket.join(conversationId);
    console.log(`User tham gia vao phong: ${conversationId}`);

    // Gửi trạng thái cuộc gọi nếu có cuộc gọi đang diễn ra trong nhóm này
    const callState = activeCalls.get(conversationId);
    if (callState && callState.isGroup) {
      socket.emit(
        "cap_nhat_trang_thai_goi_nhom",
        buildGroupCallUpdatePayload(callState),
      );
    }
  });

  socket.on("roi_nhom_chat", (conversationId) => {
    socket.leave(conversationId);
    console.log(`User roi phong: ${conversationId}`);
  });

  // Mỗi user join 1 room riêng theo userId — dùng để nhận tin nhắn và hội thoại mới
  socket.on("tham_gia_user_room", async (userId) => {
    socket.data.userId = userId;
    socket.join(`user:${userId}`);
    console.log(`User ${userId} da vao phong ca nhan`);

    // ── PRESENCE: Đánh dấu user online ──────────────────────────
    const isFirstSession = await presenceService.handleConnect(userId, socket.id);
    if (isFirstSession) {
      // Lấy danh sách bạn bè/thành viên nhóm để notify
      const friends = await getPresenceFriends(userId);
      await presenceService.broadcastOnline(io, userId, friends);
    }
    // ────────────────────────────────────────────────────────────

    // Gửi trạng thái các cuộc gọi đang diễn ra cho người dùng vừa kết nối (Sidebar sync)
    for (const [conversationId, callState] of activeCalls.entries()) {
      if (callState.isGroup && callState.memberIds && callState.memberIds.has(userId)) {
        socket.emit(
          "cap_nhat_trang_thai_goi_nhom",
          buildGroupCallUpdatePayload(callState),
        );
      }
    }
  });

  socket.on("presence_heartbeat", async (payload = {}) => {
    const userId = payload.userId || socket.data.userId;
    if (!userId) return;

    try {
      socket.data.userId = userId;
      socket.join(`user:${userId}`);

      const isFirstSession = await presenceService.handleConnect(userId, socket.id);
      if (isFirstSession) {
        const friends = await getPresenceFriends(userId);
        await presenceService.broadcastOnline(io, userId, friends);
      }
    } catch (err) {
      console.error("[Presence] presence_heartbeat error:", err.message);
    }
  });

  // ── PRESENCE: Client hỏi trạng thái nhiều user ──────────────
  socket.on("hoi_trang_thai_hoat_dong", async ({ userIds }) => {
    if (!Array.isArray(userIds) || userIds.length === 0) return;
    try {
      const statusMap = await presenceService.getBulkOnlineStatus(userIds);
      const result = [];
      statusMap.forEach((statusObj, uid) => {
        result.push({ userId: uid, ...statusObj });
      });
      socket.emit("ket_qua_trang_thai_hoat_dong", result);
    } catch (err) {
      console.error("[Presence] hoi_trang_thai_hoat_dong error:", err.message);
    }
  });
  // ────────────────────────────────────────────────────────────

  const publishReceiptFromSocket = async (publisher, payload = {}, receiptType = "delivered") => {
    const conversationId = payload.conversationId;
    const userId = payload.userId || socket.data.userId;
    const msgId = payload.msgId;

    if (!conversationId || !userId || !msgId) return;

    let participant = null;
    try {
      participant =
        receiptType === "seen"
          ? await ParticipantService.updateLastRead(conversationId, userId, msgId)
          : await ParticipantService.updateLastDelivered(conversationId, userId, msgId);

      if (!participant) {
        socket.emit("message_receipt_error", {
          conversationId,
          msgId,
          error: "Participant not found",
        });
        return;
      }

      const cursorChanged = participant.$locals?.cursorChanged !== false;

      if (cursorChanged) {
        const participantPayload = {
          user_id: participant.user_id,
          conversation_id: String(participant.conversation_id),
          last_delivered_message_id: participant.last_delivered_message_id || "0",
          last_delivered_at: participant.last_delivered_at || null,
          last_read_message_id: participant.last_read_message_id || "0",
          last_read_at: participant.last_read_at || null,
        };

        const syncPayload = {
          conversationId,
          userId,
          changedUserId: userId,
          msgId,
          receiptType,
          participant: participantPayload,
        };

        const participants = await ParticipantService.getJoinedParticipants(conversationId);
        participants.forEach((item) => {
          io.to(`user:${item.user_id}`).emit("participant_cursor_changed", syncPayload);
        });

        if (receiptType === "seen") {
          io.to(`user:${userId}`).emit("conversation_read_synced", syncPayload);
        }
      }
    } catch (error) {
      console.error("[chat receipt] local update failed:", error.message);
      socket.emit("message_receipt_error", {
        conversationId,
        msgId,
        error: error.message,
      });
      return;
    }

    if (!chatReceiptQueueEnabled || participant.$locals?.cursorChanged === false) {
      return;
    }

    try {
      await publisher({
        conversationId,
        userId,
        msgId,
        deviceId: payload.deviceId || socket.id,
      });
    } catch (error) {
      console.error("[chat receipt] publish failed:", error.message);
      socket.emit("message_receipt_error", {
        conversationId,
        msgId,
        error: error.message,
      });
    }
  };

  socket.on("message_delivered", (payload) => {
    publishReceiptFromSocket(publishMessageDelivered, payload, "delivered");
  });

  socket.on("messages_delivered_up_to", (payload) => {
    publishReceiptFromSocket(publishMessageDelivered, payload, "delivered");
  });

  socket.on("message_seen_up_to", (payload) => {
    publishReceiptFromSocket(publishMessageSeen, payload, "seen");
  });

  // Kiểm tra xem người nhận có đang bận không TRƯỚC khi mở cửa sổ gọi
  socket.on("kiem_tra_ban_goi", async ({ conversationId, callerId }) => {
    if (!conversationId || !callerId) return;
    try {
      if (isUserBusyInAnotherCall(callerId, conversationId)) {
        io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
          conversationId,
          targetUserId: callerId,
          reason: "caller_busy",
        });
        return;
      }

      const conversation = await Conversation.findById(conversationId)
        .select("type")
        .lean();

      if (conversation?.type === "group") {
        io.to(`user:${callerId}`).emit("san_sang_de_goi", { conversationId });
        return;
      }

      const participants = await ParticipantService.getParticipants(conversationId);
      const targetUserIds = participants
        .map((p) => p.user_id)
        .filter((uid) => uid && uid !== callerId);

      const busyTargets = targetUserIds.filter((uid) =>
        isUserBusyInAnotherCall(uid, conversationId),
      );

      if (busyTargets.length > 0) {
        // Có người nhận đang bận → tạo tin nhắn cuộc gọi nhỡ và thông báo cho caller
        if (claimCallOutcome(`busy:${conversationId}:${callerId}`)) {
          await createCallNotificationMessage({
            conversationId,
            senderId: callerId,
            type: "call_missed",
            content: "Cuộc gọi thoại nhỡ (Người nhận đang bận)",
          });
        }

        io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
          conversationId,
          targetUserId: busyTargets[0],
          reason: "target_busy",
        });
      } else {
        // Không ai bận → cho phép bắt đầu gọi
        io.to(`user:${callerId}`).emit("san_sang_de_goi", { conversationId });
      }
    } catch (error) {
      console.error("Loi kiem_tra_ban_goi:", error.message);
      // Fallback: cho phép gọi nếu lỗi
      io.to(`user:${callerId}`).emit("san_sang_de_goi", { conversationId });
    }
  });

  socket.on("bat_dau_goi", async ({ conversationId, callerId, callType, invitedUserIds }, ack) => {
    const acknowledge = (payload = {}) => {
      if (typeof ack === "function") ack(payload);
    };

    try {
      if (!conversationId || !callerId) {
        acknowledge({ ok: false, reason: "missing_payload" });
        return;
      }

      const existingCall = activeCalls.get(conversationId);
      if (existingCall && existingCall.status !== "ended") {
        acknowledge({
          ok: false,
          reason: "already_active",
          conversationId,
          callId: existingCall.callId,
          callType: existingCall.callType,
          isGroup: !!existingCall.isGroup,
        });
        io.to(`user:${callerId}`).emit("khong_the_tham_gia_goi", {
          conversationId,
          callId: existingCall.callId,
          reason: "already_active",
        });
        return;
      }

      socket.data.userId = callerId;
      socket.join(`user:${callerId}`);

      const participants = await ParticipantService.getParticipants(conversationId);
      const memberIdsFromDb = participants
        .map(p => normalizeId(p.user_id))
        .filter(id => !!id);

      // Nếu có danh sách mời đích danh, dùng danh sách đó. Nếu không, dùng tất cả thành viên (trường hợp 1-1 hoặc gọi cả nhóm mặc định)
      const memberIds = (invitedUserIds && Array.isArray(invitedUserIds) && invitedUserIds.length > 0)
        ? invitedUserIds.map((id) => normalizeId(id)).filter(Boolean)
        : memberIdsFromDb;

      const conversation = await Conversation.findById(conversationId);
      const isGroup = conversation && conversation.type === "group";
      const effectiveCallType = isGroup ? "video" : normalizeCallType(callType);
      const normalizedCallerId = normalizeId(callerId);
      if (isUserBusyInAnotherCall(normalizedCallerId, conversationId)) {
        io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
          conversationId,
          targetUserId: normalizedCallerId,
          reason: "caller_busy",
        });
        acknowledge({ ok: false, reason: "caller_busy", targetUserId: normalizedCallerId });
        return;
      }

      const targetUserIds = memberIds.filter((userId) => userId && userId !== normalizedCallerId);
      const busyTargets = targetUserIds.filter((userId) =>
        isUserBusyInAnotherCall(userId, conversationId),
      );
      const availableTargetUserIds = isGroup
        ? targetUserIds
            .filter((userId) => !busyTargets.includes(userId))
            .slice(0, Math.max(0, MAX_GROUP_CALL_PARTICIPANTS - 1))
        : targetUserIds;

      if (!isGroup && busyTargets.length > 0) {
        if (claimCallOutcome(`busy:${conversationId}:${callerId}`)) {
          await createCallNotificationMessage({
            conversationId,
            senderId: callerId,
            type: "call_missed",
            content: `Cuộc gọi ${effectiveCallType === "video" ? "video" : "thoại"} nhỡ (Người nhận đang bận)`,
            systemMeta: {
              callType: effectiveCallType,
              outcome: "busy",
              reason: "busy",
            },
          });
        }

        io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
          conversationId,
          targetUserId: busyTargets[0],
          reason: "target_busy",
        });
        acknowledge({ ok: false, reason: "busy", targetUserId: busyTargets[0] });
        return;
      }

      const callState = ensureCallState(conversationId, effectiveCallType, memberIdsFromDb, isGroup);
      callState.isGroup = !!isGroup;

      if (!callState.initiatorId) {
        callState.initiatorId = normalizedCallerId;
      }
      callState.status = "ringing";
      callState.ringingMemberIds = new Set(availableTargetUserIds);
      callState.participants.add(normalizedCallerId);
      setActiveParticipantSocket(callState, normalizedCallerId, socket.id);

      socket.join(getCallRoomName(callState));

      let livekitToken = null;
      if (isGroup) {
        livekitToken = await generateCallLiveKitToken(callState, conversationId, callerId);
        callState.isGroup = true;
      }
      const callParticipantPayload = await buildCallParticipantPayload(conversationId, callState);

      io.to(getCallRoomName(callState)).emit("nguoi_dung_tham_gia_goi", {
        conversationId,
        callId: callState.callId,
        userId: callerId,
        callType: callState.callType,
        participants: callParticipantPayload.participants,
        participantDetails: callParticipantPayload.participantDetails,
        isGroup,
        livekitToken,
      });

      if (isGroup) {
        emitGroupCallUpdate(callState);
      }

      // Lọc bỏ người gọi và người đang bận ở cuộc gọi khác khỏi danh sách nhận thông báo.
      availableTargetUserIds.forEach((userId) => {
        io.to(`user:${userId}`).emit("cuoc_goi_den", {
          conversationId,
          callId: callState.callId,
          callerId,
          callType: callState.callType,
          startedAt: callState.startedAt,
          isGroup,
        });
      });

      const successPayload = {
        conversationId,
        callId: callState.callId,
        callType: callState.callType,
        participants: callParticipantPayload.participants,
        participantDetails: callParticipantPayload.participantDetails,
        isGroup,
        livekitToken,
      };
      io.to(`user:${callerId}`).emit("bat_dau_goi_thanh_cong", successPayload);

      scheduleNoAnswerTimeout({
        conversationId,
        callerId,
        callType: callState.callType,
      });
      acknowledge({ ok: true, ...successPayload });
    } catch (error) {
      console.error("Loi bat_dau_goi:", error.message);
      acknowledge({ ok: false, reason: "server_error", message: error.message });
    }
  });

  socket.on(
    "tham_gia_cuoc_goi",
    async ({ conversationId, userId, callType, callId }, ack) => {
      const acknowledge = (payload = {}) => {
        if (typeof ack === "function") ack(payload);
      };

      if (!conversationId || !userId) {
        acknowledge({ ok: false, reason: "missing_payload" });
        return;
      }

      if (isUserBusyInAnotherCall(userId, conversationId)) {
        io.to(`user:${userId}`).emit("khong_the_tham_gia_goi", {
          conversationId,
          callId,
          reason: "busy",
        });
        acknowledge({ ok: false, reason: "busy" });
        return;
      }

      socket.data.userId = userId;
      socket.join(`user:${userId}`);

      const participantsDb = await ParticipantService.getParticipants(conversationId);
      const memberIdsFromDb = participantsDb.map(p => normalizeId(p.user_id)).filter(id => !!id);
      const conversation = await Conversation.findById(conversationId);
      const isGroup = conversation && conversation.type === "group";

      const callState = activeCalls.get(conversationId);
      if (!callState || !isSameCall(callState, callId)) {
        io.to(`user:${userId}`).emit("ket_thuc_phong_goi", {
          conversationId,
          callId,
          endedBy: null,
          reason: "call_not_found",
        });
        acknowledge({ ok: false, reason: "call_not_found" });
        return;
      }

      callState.isGroup = callState.isGroup || !!isGroup;
      callState.callType = callState.isGroup
        ? "video"
        : normalizeCallType(callState.callType || callType);
      callState.memberIds = new Set([
        ...Array.from(callState.memberIds || []),
        ...memberIdsFromDb,
      ]);
      const normalizedJoinUserId = normalizeId(userId);
      const wasAlreadyParticipant = callState.participants.has(normalizedJoinUserId);
      const activeJoinSocketId = getActiveParticipantSocketId(callState, normalizedJoinUserId);
      if (
        wasAlreadyParticipant &&
        activeJoinSocketId &&
        activeJoinSocketId !== socket.id &&
        isSocketConnected(activeJoinSocketId)
      ) {
        emitAnsweredElsewhereToCurrentSocket(
          socket,
          callState,
          normalizedJoinUserId,
          "already_joined_elsewhere",
        );
        acknowledge({
          ok: false,
          reason: "already_joined_elsewhere",
          conversationId,
          callId: callState.callId,
          isGroup: callState.isGroup,
        });
        return;
      }

      if (
        callState.isGroup &&
        !wasAlreadyParticipant &&
        callState.participants.size >= MAX_GROUP_CALL_PARTICIPANTS
      ) {
        io.to(`user:${userId}`).emit("khong_the_tham_gia_goi", {
          conversationId,
          callId: callState.callId,
          reason: "group_call_full",
        });
        acknowledge({
          ok: false,
          reason: "group_call_full",
          conversationId,
          callId: callState.callId,
          isGroup: true,
        });
        return;
      }
      clearParticipantDisconnectTimer(callState, normalizedJoinUserId);
      callState.ringingMemberIds?.delete(normalizedJoinUserId);
      callState.declinedMemberIds?.delete(normalizedJoinUserId);
      callState.participants.add(normalizedJoinUserId);
      setActiveParticipantSocket(callState, normalizedJoinUserId, socket.id);
      notifyOtherDevicesAnsweredElsewhere(socket, callState, normalizedJoinUserId);

      if (!callState.answeredAt && callState.participants.size >= 2) {
        callState.answeredAt = new Date().toISOString();
        callState.status = "accepted";
      }

      if (callState.participants.size >= 2) {
        callState.hadMultipleParticipants = true;
        // Chỉ xóa timer nếu không phải gọi nhóm, hoặc nếu gọi nhóm mà tất cả thành viên đã vào đủ
        const shouldClearTimer = !callState.isGroup ||
          (callState.memberIds && callState.participants.size >= callState.memberIds.size);

        if (shouldClearTimer && callState.noAnswerTimer) {
          clearTimeout(callState.noAnswerTimer);
          callState.noAnswerTimer = null;
        }
      }

      socket.join(getCallRoomName(callState));

      let livekitToken = null;
      if (callState.isGroup) {
        livekitToken = await generateCallLiveKitToken(callState, conversationId, userId);
      }
      const callParticipantPayload = await buildCallParticipantPayload(conversationId, callState);

      const joinedPayload = {
        conversationId,
        callId: callState.callId,
        userId,
        callType: callState.callType,
        participants: callParticipantPayload.participants,
        participantDetails: callParticipantPayload.participantDetails,
        isGroup: callState.isGroup,
        livekitToken,
      };

      io.to(getCallRoomName(callState)).emit("nguoi_dung_tham_gia_goi", joinedPayload);

      // Quan trọng: Gửi token cho chính người vừa tham gia để họ mở màn hình gọi
      if (callState.isGroup) {
        io.to(`user:${userId}`).emit("bat_dau_goi_thanh_cong", {
          conversationId,
          callId: callState.callId,
          userId,
          callType: callState.callType,
          participants: callParticipantPayload.participants,
          participantDetails: callParticipantPayload.participantDetails,
          isGroup: true,
          livekitToken,
        });

        emitGroupCallUpdate(callState);

        if (!wasAlreadyParticipant) {
          void createCallJoinNotificationMessage({
            conversationId,
            userId,
            callState,
          });
        }
      }

      acknowledge({ ok: true, ...joinedPayload });
    },
  );

  socket.on(
    "gui_offer",
    ({ conversationId, callId, fromUserId, targetUserId, offer, callType }) => {
      if (!conversationId || !fromUserId || !targetUserId || !offer) return;
      const callState = activeCalls.get(conversationId);
      if (!callState || !isSameCall(callState, callId)) return;

      io.to(`user:${targetUserId}`).emit("nhan_offer", {
        conversationId,
        callId: callState.callId,
        fromUserId,
        offer,
        callType: normalizeCallType(callType || callState.callType),
      });
    },
  );

  socket.on(
    "gui_answer",
    ({ conversationId, callId, fromUserId, targetUserId, answer }) => {
      if (!conversationId || !fromUserId || !targetUserId || !answer) return;
      const callState = activeCalls.get(conversationId);
      if (!callState || !isSameCall(callState, callId)) return;

      io.to(`user:${targetUserId}`).emit("nhan_answer", {
        conversationId,
        callId: callState.callId,
        fromUserId,
        answer,
      });
    },
  );

  socket.on(
    "gui_ice_candidate",
    ({ conversationId, callId, fromUserId, targetUserId, candidate }) => {
      if (!conversationId || !fromUserId || !targetUserId || !candidate) return;
      const callState = activeCalls.get(conversationId);
      if (!callState || !isSameCall(callState, callId)) return;

      io.to(`user:${targetUserId}`).emit("nhan_ice_candidate", {
        conversationId,
        callId: callState.callId,
        fromUserId,
        candidate,
      });
    },
  );

    socket.on("moi_them_thanh_vien_goi", async ({ conversationId, callId, targetUserIds, callerId }) => {
      if (!conversationId || !targetUserIds || !Array.isArray(targetUserIds)) return;
      const convIdStr = String(conversationId);
      console.log(`[CALL] Moi them thanh vien: conv=${convIdStr}, targets=${targetUserIds}, caller=${callerId}`);

      const callState = activeCalls.get(convIdStr);
      if (!callState || !isSameCall(callState, callId)) return;

      const remainingSlots = callState.isGroup
        ? Math.max(0, MAX_GROUP_CALL_PARTICIPANTS - callState.participants.size)
        : targetUserIds.length;
      if (remainingSlots <= 0) {
        io.to(`user:${callerId}`).emit("khong_the_tham_gia_goi", {
          conversationId: convIdStr,
          callId: callState.callId,
          reason: "group_call_full",
        });
        return;
      }

      const availableTargetUserIds = Array.from(new Set(
        targetUserIds
          .map((userId) => normalizeId(userId))
          .filter(Boolean),
      )).filter((userIdStr) => {
        // Không mời người đã có trong cuộc gọi
        if (callState.participants.has(userIdStr)) return false;
        return !isUserBusyInAnotherCall(userIdStr, convIdStr);
      }).slice(0, remainingSlots);

      availableTargetUserIds.forEach(userIdStr => {

        // Thêm vào memberIds nếu chưa có (để signaling cancel sau này)
        if (callState.memberIds) {
          callState.memberIds.add(userIdStr);
        }
        if (callState.ringingMemberIds) {
          callState.ringingMemberIds.add(userIdStr);
        }

        // Gửi thông báo cuộc gọi đến
        io.to(`user:${userIdStr}`).emit("cuoc_goi_den", {
          conversationId: convIdStr,
          callId: callState.callId,
          callerId: String(callState.initiatorId || callerId),
          callType: callState.callType,
          isGroup: true,
          participants: Array.from(callState.participants),
          startedAt: callState.startedAt,
        });

        // Cập nhật trạng thái gọi cho họ ngay trên sidebar
        io.to(`user:${userIdStr}`).emit(
          "cap_nhat_trang_thai_goi_nhom",
          buildGroupCallUpdatePayload(callState),
        );
      });
    });

    socket.on("dang_xuat", async ({ userId }, ack) => {
      const acknowledge = (payload = {}) => {
        if (typeof ack === "function") ack(payload);
      };

      const logoutUserId = normalizeId(userId || socket.data.userId);
      if (!logoutUserId) {
        acknowledge({ ok: false, reason: "missing_user" });
        return;
      }

      try {
        await removeUserFromAllCalls(logoutUserId, "logout");
        acknowledge({ ok: true });
      } catch (error) {
        console.error("Loi don dep cuoc goi khi dang xuat:", error.message);
        acknowledge({ ok: false, reason: "server_error" });
      }
    });

    socket.on("chap_nhan_goi", ({ conversationId, userId, callId }) => {
      if (!conversationId || !userId) return;

      const callState = activeCalls.get(conversationId);
      if (!callState || !isSameCall(callState, callId)) return;
    });

  socket.on("roi_cuoc_goi", ({ conversationId, userId, callId }, ack) => {
    const acknowledge = (payload = {}) => {
      if (typeof ack === "function") ack(payload);
    };
    if (!conversationId || !userId) {
      acknowledge({ ok: false, reason: "missing_payload" });
      return;
    }

    removeParticipantFromCall({
      conversationId,
      userId,
      callId,
      socketToLeave: socket,
      reason: "left",
    })
      .then(acknowledge)
      .catch((error) => {
        console.error("Loi roi_cuoc_goi:", error.message);
        acknowledge({ ok: false, reason: "server_error" });
      });
  });

  socket.on("tu_choi_goi", async ({ conversationId, callId, userId, callerId }, ack) => {
    const acknowledge = (payload = {}) => {
      if (typeof ack === "function") ack(payload);
    };
    if (!conversationId || !userId || !callerId) {
      acknowledge({ ok: false, reason: "missing_payload" });
      return;
    }

    const normalizedDeclineUserId = normalizeId(userId);
    const callState = activeCalls.get(conversationId);
    if (!callState || !isSameCall(callState, callId)) {
      socket.emit("ket_thuc_phong_goi", {
        conversationId,
        callId,
        endedBy: userId,
        reason: "call_not_found",
      });
      acknowledge({ ok: false, reason: "call_not_found" });
      return;
    }

    if (
      isParticipantActiveOnAnotherDevice(callState, normalizedDeclineUserId, socket.id) ||
      (callState.participants.has(normalizedDeclineUserId) && hasCallBeenAnswered(callState))
    ) {
      emitAnsweredElsewhereToCurrentSocket(
        socket,
        callState,
        normalizedDeclineUserId,
        "stale_device_ignored",
      );
      socket.emit("ket_thuc_phong_goi", {
        conversationId,
        callId: callState.callId,
        endedBy: normalizedDeclineUserId,
        reason: "stale_device_ignored",
      });
      acknowledge({ ok: true, reason: "stale_device_ignored", callId: callState.callId });
      return;
    }

    io.to(`user:${callerId}`).emit("nguoi_dung_tu_choi_goi", {
      conversationId,
      callId: callState.callId,
      userId,
    });

    callState.declinedMemberIds?.add(normalizedDeclineUserId);
    callState.ringingMemberIds?.delete(normalizedDeclineUserId);

    // Nếu là nhóm, chỉ đóng modal của người từ chối, không đóng cả phòng
    if (callState && callState.isGroup) {
      io.to(`user:${normalizedDeclineUserId}`).emit("ket_thuc_phong_goi", {
        conversationId,
        callId: callState.callId,
        endedBy: userId,
        reason: "declined",
      });
      acknowledge({ ok: true, reason: "declined", callId: callState.callId });
      return;
    }

    const result = await finishCall({
      conversationId,
      callId: callState.callId,
      endedBy: userId,
      outcome: hasCallBeenAnswered(callState) ? "completed" : "missed",
      reason: "declined",
    });
    acknowledge(result);
  });

  socket.on("ket_thuc_goi", async ({ conversationId, callId, userId, callType, wasConnected, durationSeconds }, ack) => {
    const acknowledge = (payload = {}) => {
      if (typeof ack === "function") {
        ack(payload);
      }
    };

    if (!conversationId) {
      acknowledge({ ok: false, reason: "missing_conversation" });
      return;
    }

    const endingUserId = userId || socket.data.userId || "";
    const callState = activeCalls.get(conversationId);
    if (!callState || !isSameCall(callState, callId)) {
      console.warn(`[CALL] ket_thuc_goi missing active call: conversationId=${conversationId}, callId=${callId || ""}, userId=${endingUserId}`);
      const fallbackCreated = await emitFallbackCallOutcomeForMissingState({
        conversationId,
        userId: endingUserId,
        callId,
        callType,
        wasConnected,
        durationSeconds,
      });
      acknowledge({
        ok: fallbackCreated,
        reason: fallbackCreated ? "fallback_outcome_created" : "call_not_found",
      });
      return;
    }

    console.log(
      `[CALL] ket_thuc_goi received: conversationId=${conversationId}, callId=${callState.callId || ""}, userId=${endingUserId}, callType=${callType || callState.callType || ""}, wasConnected=${!!wasConnected}, durationSeconds=${durationSeconds ?? ""}, participants=${callState.participants.size}, answeredAt=${callState.answeredAt || ""}, hadMultipleParticipants=${!!callState.hadMultipleParticipants}`,
    );

    // Restore: Nếu là cuộc gọi nhóm, hành động "Kết thúc" của 1 người chỉ là "Rời đi"
    if (isParticipantActiveOnAnotherDevice(callState, endingUserId, socket.id)) {
      emitAnsweredElsewhereToCurrentSocket(
        socket,
        callState,
        endingUserId,
        "stale_device_ignored",
      );
      acknowledge({
        ok: true,
        reason: "stale_device_ignored",
        callId: callState.callId,
      });
      return;
    }

    if (callState.isGroup) {
      const leaveResult = await removeParticipantFromCall({
        conversationId,
        userId: endingUserId,
        callId: callState.callId,
        socketToLeave: socket,
        reason: "left",
      });
      acknowledge({ ok: leaveResult.ok, mode: "left_group", callId: leaveResult.callId });
      return;
    }

    const outcome =
      hasCallBeenAnswered(callState) || wasConnected || Number(durationSeconds) > 0
        ? "completed"
        : resolveUnansweredOutcome(callState, endingUserId, "cancelled");
    const result = await finishCall({
      conversationId,
      callId: callState.callId,
      endedBy: endingUserId || null,
      outcome,
      durationSeconds,
      reason: outcome,
    });
    acknowledge(result);
    console.log(`Cuoc goi ket thuc tai phong ${conversationId}`);
  });

  socket.on("trang_thai_camera", ({ conversationId, callId, userId, isCameraOff }) => {
    if (!conversationId || !userId) return;
    const callState = activeCalls.get(conversationId);
    if (!callState || !isSameCall(callState, callId)) return;

    io.to(getCallRoomName(callState)).emit("thay_doi_trang_thai_camera", {
      conversationId,
      callId: callState.callId,
      userId,
      isCameraOff,
    });
  });

  socket.on("disconnecting", async () => {
    const userId = socket.data.userId;
    if (!userId) return;

    // Nếu socket đang trong phòng gọi "call:...", dọn dẹp ngay khi đóng tab
    // Lưu ý: socket.rooms chứa phòng của chính socket đó và các phòng nó tham gia
    const rooms = Array.from(socket.rooms);
    const callRoomId = rooms.find(r => r.startsWith("call:"));

    if (callRoomId) {
      console.log(`Socket cua user ${userId} trong phong ${callRoomId} dang ngat ket noi (disconnecting), doi reconnect grace ${CALL_RECONNECT_GRACE_MS}ms.`);
      scheduleUserDisconnectFromAllCalls(userId, socket.id);
    }
  });

  socket.on("disconnect", async () => {
    const userId = socket.data.userId;
    if (!userId) {
      console.log("Socket disconnect: no userId");
      return;
    }

    // ── PRESENCE: Cập nhật Redis khi socket ngắt ────────────────
    const { isFullyOffline } = await presenceService.handleDisconnect(userId, socket.id);
    if (isFullyOffline) {
      // Dùng debounce để tránh nhấp nháy trạng thái khi mạng chập chờn
      presenceService.scheduleOfflineBroadcast(io, userId, getPresenceFriends);
    }
    // ────────────────────────────────────────────────────────────

    // Kiểm tra xem user còn socket nào khác đang online không (ví dụ: tab CallPage hoặc tab chat khác)
    const activeSockets = await io.in(`user:${userId}`).fetchSockets();

    if (activeSockets.length === 0) {
      console.log(`User ${userId} da ngat ket noi hoan toan. Len lich don dep cuoc goi sau reconnect grace...`);
      scheduleUserDisconnectFromAllCalls(userId, socket.id);
    } else {
      console.log(`User ${userId} ngat ket noi 1 socket, nhung van con ${activeSockets.length} socket khac hoat dong.`);
    }
  });
});

// ============================================================
// PRESENCE HELPER: Lấy danh sách bạn bè / thành viên nhóm
// để biết cần notify ai khi user online/offline.
// ============================================================
const getPresenceFriends = async (userId) => {
  try {
    const participants = await ParticipantService.getConversationsByUserId(userId);
    const friendSet = new Set();
    for (const p of participants) {
      // conversation_id có thể là ObjectId hoặc populated document
      const convId = p.conversation_id?._id
        ? p.conversation_id._id.toString()
        : p.conversation_id?.toString();

      if (!convId) continue;

      const convParticipants = await ParticipantService.getParticipants(convId);
      convParticipants.forEach((cp) => {
        if (String(cp.user_id) !== String(userId)) {
          friendSet.add(String(cp.user_id));
        }
      });
    }
    return Array.from(friendSet);
  } catch (err) {
    console.error("[Presence] getPresenceFriends error:", err.message);
    return [];
  }
};


// ========== PRESENCE REST API (để query trạng thái) ==========
app.get("/api/presence/:userId", async (req, res) => {
  try {
    const { userId } = req.params;
    const online = await presenceService.isOnline(userId);
    res.json({ userId, isOnline: online });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post("/api/presence/bulk", async (req, res) => {
  try {
    const { userIds } = req.body;
    if (!Array.isArray(userIds)) {
      return res.status(400).json({ error: "userIds must be an array" });
    }
    const statusMap = await presenceService.getBulkOnlineStatus(userIds);
    const result = {};
    statusMap.forEach((statusObj, uid) => {
      result[uid] = statusObj;
    });
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});
// ============================================================

// ========== MESSAGE ROUTES ==========
app.use("/api", messageRoutes);

// ========== OTHER ROUTES ==========
app.use("/api", apiRoutes);
// Redundant mounting to prevent 404s from various gateway mappings.
app.use("/api/ai", aiRoutes);
app.use("/ai", aiRoutes);
app.use("/api/chat/ai", aiRoutes);
app.use("/riff/api/ai", aiRoutes);
app.use("/riff/api/chat/ai", aiRoutes);

app.get("/", (req, res) => res.send("Chat Service dang chay..."));

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Chat Service dang chay tren port ${PORT}`);
  presenceService.cleanupStalePresenceOnStartup();
  presenceService.startPresenceReconciler();
});
