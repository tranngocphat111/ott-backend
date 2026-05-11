const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const cors = require("cors");

const connectDB = require("./config/db");
const apiRoutes = require("./routes/api");
const messageRoutes = require("./routes/messageRoutes");
const messageEventsHandler = require("./events/messageEvents");
const ParticipantService = require("./services/participantService");
const MessageService = require("./services/messageService");
const { initAllConsumers } = require("./consumers");
const Conversation = require("./models/Conversation");
const livekitService = require("./services/livekitService");
const { activeCalls } = require("./services/callStateService");
connectDB();
const app = express();
const server = http.createServer(app);

app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ limit: "10mb", extended: true }));

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
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

const isUserBusyInAnotherCall = (userId, conversationId) => {
  if (!userId) return false;

  for (const [activeConversationId, callState] of activeCalls.entries()) {
    if (activeConversationId === conversationId) continue;
    if (callState.participants.has(userId)) {
      return true;
    }
  }

  return false;
};

const endCallRoom = async (conversationId, endedBy = null) => {
  const callState = activeCalls.get(conversationId);
  if (!callState) return;

  if (callState.noAnswerTimer) {
    clearTimeout(callState.noAnswerTimer);
    callState.noAnswerTimer = null;
  }

  const payload = {
    conversationId: String(conversationId),
    endedBy: endedBy ? String(endedBy) : null,
  };

  console.log(`[CALL] endCallRoom: conversationId=${payload.conversationId}, endedBy=${payload.endedBy}`);

  io.to(`call:${conversationId}`).emit("ket_thuc_phong_goi", payload);

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

  io.in(`call:${conversationId}`).socketsLeave(`call:${conversationId}`);
  if (callState.isGroup) {
    const updatePayload = {
      conversationId: String(conversationId),
      isCalling: false,
      participantCount: 0,
    };
    io.to(`conversation:${conversationId}`).emit("cap_nhat_trang_thai_goi_nhom", updatePayload);
    if (callState.memberIds) {
      callState.memberIds.forEach((uid) => {
        io.to(`user:${uid}`).emit("cap_nhat_trang_thai_goi_nhom", updatePayload);
      });
    }
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
}) => {
  if (!conversationId || !senderId || !type || !content) return;

  try {
    const message = await MessageService.sendMessage({
      conversationId,
      senderId,
      content,
      type,
      size: 0,
    });

    await emitMessageToConversationParticipants(conversationId, message);
  } catch (error) {
    console.error("Khong the tao thong bao cuoc goi:", error.message);
  }
};

const scheduleNoAnswerTimeout = ({ conversationId, callerId, callType }) => {
  const callState = activeCalls.get(conversationId);
  if (!callState) return;

  if (callState.noAnswerTimer) {
    clearTimeout(callState.noAnswerTimer);
  }

  callState.noAnswerTimer = setTimeout(async () => {
    const currentCallState = activeCalls.get(conversationId);
    if (!currentCallState) return;

    console.log(`[CALL] No-answer timeout (30s) reached for conversation ${conversationId}.`);

    // Tự động "từ chối" cho những người chưa bắt máy sau 30s
    if (currentCallState.memberIds) {
      currentCallState.memberIds.forEach(uid => {
        if (!currentCallState.participants.has(uid)) {
          // Gửi tín hiệu để client tự động đóng modal/dừng đổ chuông
          io.to(`user:${uid}`).emit("ket_thuc_phong_goi", {
            conversationId,
            reason: "timeout",
            message: "Cuộc gọi đã kết thúc do không trả lời"
          });
          console.log(`[CALL] Auto-rejecting for user ${uid} (timeout)`);
        }
      });
    }

    // Thực sự đóng phòng nếu không có ai khác tham gia sau 30s (chỉ còn tối đa 1 người là người gọi)
    const shouldCloseRoom = currentCallState.participants.size <= 1;

    if (shouldCloseRoom) {
      if (!currentCallState.isOutcomeEmitted) {
        currentCallState.isOutcomeEmitted = true;
        await createCallNotificationMessage({
          conversationId,
          senderId: callerId,
          type: "call_missed",
          content: `Cuộc gọi ${callType === "video" ? "video" : "thoại"} nhỡ`,
        });
      }

      console.log(`[CALL] Room ${conversationId} closed due to no response.`);
      endCallRoom(conversationId, callerId);
    } else {
      console.log(`[CALL] Room ${conversationId} remains active for ${currentCallState.participants.size} participants.`);
    }
  }, NO_ANSWER_TIMEOUT_MS);
};

const emitCallOutcomeMessage = async ({
  conversationId,
  senderId,
  callType,
  outcome,
  answeredAt,
  endedAt = new Date().toISOString(),
}) => {
  if (!conversationId || !senderId) return;

  if (outcome === "completed") {
    const startAt = answeredAt ? new Date(answeredAt).getTime() : null;
    const endAt = new Date(endedAt).getTime();
    const durationSeconds = startAt
      ? Math.max(0, Math.floor((endAt - startAt) / 1000))
      : 0;

    await createCallNotificationMessage({
      conversationId,
      senderId,
      type: "call_end",
      content: `Cuộc gọi ${callType === "video" ? "video" : "thoại"} - ${formatCallDuration(durationSeconds)}`,
    });
    return;
  }

  await createCallNotificationMessage({
    conversationId,
    senderId,
    type: "call_missed",
    content: `Cuộc gọi ${callType === "video" ? "video" : "thoại"} nhỡ`,
  });
};


const ensureCallState = (conversationId, callType, memberIds = []) => {
  if (!activeCalls.has(conversationId)) {
    activeCalls.set(conversationId, {
      conversationId,
      callType,
      participants: new Set(),
      memberIds: new Set(memberIds), // Lưu danh sách member để signaling cancel
      startedAt: new Date().toISOString(),
      answeredAt: null,
      isOutcomeEmitted: false,
      isGroup: false,
      hadMultipleParticipants: false,
      noAnswerTimer: null,
    });
  }
  return activeCalls.get(conversationId);
};

const removeUserFromAllCalls = (userId) => {
  if (!userId) return;

  for (const [conversationId, callState] of activeCalls.entries()) {
    if (!callState.participants.has(userId)) {
      continue;
    }

    callState.participants.delete(userId);

    io.to(`call:${conversationId}`).emit("nguoi_dung_roi_goi", {
      conversationId,
      userId,
      participants: Array.from(callState.participants),
    });

    // Nếu không còn ai trong phòng và cuộc gọi chưa được trả lời -> Kết thúc hoàn toàn
    if (callState.participants.size === 0) {
      if (!callState.answeredAt && String(callState.initiatorId) === String(userId)) {
        void emitCallOutcomeMessage({
          conversationId,
          senderId: userId,
          callType: callState.callType,
          outcome: "missed",
        });
      }
      endCallRoom(conversationId, userId);
      continue;
    }

    maybeCloseCallWhenOnlyOneLeft(conversationId, userId);
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
  });

  socket.on("roi_nhom_chat", (conversationId) => {
    socket.leave(conversationId);
    console.log(`User roi phong: ${conversationId}`);
  });

  // Mỗi user join 1 room riêng theo userId — dùng để nhận tin nhắn và hội thoại mới
  socket.on("tham_gia_user_room", (userId) => {
    socket.data.userId = userId;
    socket.join(`user:${userId}`);
    console.log(`User ${userId} da vao phong ca nhan`);
  });

  // Kiểm tra xem người nhận có đang bận không TRƯỚC khi mở cửa sổ gọi
  socket.on("kiem_tra_ban_goi", async ({ conversationId, callerId }) => {
    if (!conversationId || !callerId) return;
    try {
      const participants = await ParticipantService.getParticipants(conversationId);
      const targetUserIds = participants
        .map((p) => p.user_id)
        .filter((uid) => uid && uid !== callerId);

      const busyTargets = targetUserIds.filter((uid) =>
        isUserBusyInAnotherCall(uid, conversationId),
      );

      if (busyTargets.length > 0) {
        // Có người nhận đang bận → tạo tin nhắn cuộc gọi nhỡ và thông báo cho caller
        await createCallNotificationMessage({
          conversationId,
          senderId: callerId,
          type: "call_missed",
          content: "Cuộc gọi thoại nhỡ (Người nhận đang bận)",
        });

        io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
          conversationId,
          targetUserId: busyTargets[0],
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

  socket.on("bat_dau_goi", async ({ conversationId, callerId, callType, invitedUserIds }) => {
    try {
      if (!conversationId || !callerId) return;

      socket.data.userId = callerId;
      socket.join(`user:${callerId}`);

      const participants = await ParticipantService.getParticipants(conversationId);
      const memberIdsFromDb = participants.map(p => p.user_id).filter(id => !!id);

      // Nếu có danh sách mời đích danh, dùng danh sách đó. Nếu không, dùng tất cả thành viên (trường hợp 1-1 hoặc gọi cả nhóm mặc định)
      const memberIds = (invitedUserIds && Array.isArray(invitedUserIds) && invitedUserIds.length > 0)
        ? invitedUserIds
        : memberIdsFromDb;

      const conversation = await Conversation.findById(conversationId);
      const isGroup = conversation && conversation.type === "group";

      const callState = ensureCallState(conversationId, callType, memberIdsFromDb);
      callState.isGroup = !!isGroup;

      if (!callState.initiatorId) {
        callState.initiatorId = callerId;
      }
      callState.participants.add(callerId);

      socket.join(`call:${conversationId}`);

      let livekitToken = null;
      if (isGroup) {
        livekitToken = await livekitService.generateToken(conversationId, callerId);
        callState.isGroup = true;
      }

      io.to(`call:${conversationId}`).emit("nguoi_dung_tham_gia_goi", {
        conversationId,
        userId: callerId,
        callType: callState.callType,
        participants: Array.from(callState.participants),
        isGroup,
        livekitToken,
      });

      if (isGroup) {
        // Emit to the conversation room for those already inside the chat
        io.to(`conversation:${conversationId}`).emit("cap_nhat_trang_thai_goi_nhom", {
          conversationId,
          isCalling: true,
          participantCount: callState.participants.size,
        });

        // Also emit to each member's individual room to update their sidebars
        if (callState.memberIds) {
          callState.memberIds.forEach(uid => {
            io.to(`user:${uid}`).emit("cap_nhat_trang_thai_goi_nhom", {
              conversationId,
              isCalling: true,
              participantCount: callState.participants.size,
            });
          });
        }
      }

      // Lọc bỏ người gọi khỏi danh sách nhận thông báo
      const targetUserIds = memberIds.filter((userId) => userId && userId !== callerId);

      targetUserIds.forEach((userId) => {
        if (isUserBusyInAnotherCall(userId, conversationId)) {
          io.to(`user:${callerId}`).emit("nguoi_dung_ban_goi", {
            conversationId,
            targetUserId: userId,
          });
          return;
        }

        io.to(`user:${userId}`).emit("cuoc_goi_den", {
          conversationId,
          callerId,
          callType: callState.callType,
          startedAt: callState.startedAt,
          isGroup,
        });
      });

      io.to(`user:${callerId}`).emit("bat_dau_goi_thanh_cong", {
        conversationId,
        callType: callState.callType,
        isGroup,
        livekitToken,
      });

      scheduleNoAnswerTimeout({
        conversationId,
        callerId,
        callType: callState.callType,
      });
    } catch (error) {
      console.error("Loi bat_dau_goi:", error.message);
    }
  });

  socket.on(
    "tham_gia_cuoc_goi",
    async ({ conversationId, userId, callType }) => {
      if (!conversationId || !userId) return;

      if (isUserBusyInAnotherCall(userId, conversationId)) {
        io.to(`user:${userId}`).emit("khong_the_tham_gia_goi", {
          conversationId,
          reason: "busy",
        });
        return;
      }

      socket.data.userId = userId;
      socket.join(`user:${userId}`);

      const callState = ensureCallState(conversationId, callType);
      const participantCountBeforeJoin = callState.participants.size;
      callState.participants.add(userId);

      if (!callState.answeredAt && callState.participants.size >= 2) {
        callState.answeredAt = new Date().toISOString();
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

      socket.join(`call:${conversationId}`);

      const conversation = await Conversation.findById(conversationId);
      const isGroup = conversation && conversation.type === "group";
      let livekitToken = null;
      if (isGroup) {
        livekitToken = await livekitService.generateToken(conversationId, userId);
        callState.isGroup = true;
      }

      io.to(`call:${conversationId}`).emit("nguoi_dung_tham_gia_goi", {
        conversationId,
        userId,
        callType: callState.callType,
        participants: Array.from(callState.participants),
        isGroup,
        livekitToken,
      });

      if (isGroup) {
        io.to(`conversation:${conversationId}`).emit("cap_nhat_trang_thai_goi_nhom", {
          conversationId,
          isCalling: true,
          participantCount: callState.participants.size,
        });
      }
    },
  );

  socket.on(
    "gui_offer",
    ({ conversationId, fromUserId, targetUserId, offer, callType }) => {
      if (!conversationId || !fromUserId || !targetUserId || !offer) return;

      io.to(`user:${targetUserId}`).emit("nhan_offer", {
        conversationId,
        fromUserId,
        offer,
        callType: normalizeCallType(callType),
      });
    },
  );

  socket.on(
    "gui_answer",
    ({ conversationId, fromUserId, targetUserId, answer }) => {
      if (!conversationId || !fromUserId || !targetUserId || !answer) return;

      io.to(`user:${targetUserId}`).emit("nhan_answer", {
        conversationId,
        fromUserId,
        answer,
      });
    },
  );

  socket.on(
    "gui_ice_candidate",
    ({ conversationId, fromUserId, targetUserId, candidate }) => {
      if (!conversationId || !fromUserId || !targetUserId || !candidate) return;

      io.to(`user:${targetUserId}`).emit("nhan_ice_candidate", {
        conversationId,
        fromUserId,
        candidate,
      });
    },
  );

  socket.on("roi_cuoc_goi", ({ conversationId, userId }) => {
    if (!conversationId || !userId) return;

    const callState = activeCalls.get(conversationId);
    if (!callState) return;

    callState.participants.delete(userId);
    socket.leave(`call:${conversationId}`);

    io.to(`call:${conversationId}`).emit("nguoi_dung_roi_goi", {
      conversationId,
      userId,
      participants: Array.from(callState.participants),
    });

    if (callState.isGroup) {
      io.to(`conversation:${conversationId}`).emit("cap_nhat_trang_thai_goi_nhom", {
        conversationId,
        isCalling: callState.participants.size > 0,
        participantCount: callState.participants.size,
      });
    }

    if (callState.participants.size === 0) {
      activeCalls.delete(conversationId);
      return;
    }

    maybeCloseCallWhenOnlyOneLeft(conversationId, userId);
  });

  socket.on("tu_choi_goi", async ({ conversationId, userId, callerId }) => {
    if (!conversationId || !userId || !callerId) return;

    io.to(`user:${callerId}`).emit("nguoi_dung_tu_choi_goi", {
      conversationId,
      userId,
    });

    const callState = activeCalls.get(conversationId);

    // Nếu là nhóm, chỉ đóng modal của người từ chối, không đóng cả phòng
    if (callState && callState.isGroup) {
      io.to(`user:${userId}`).emit("ket_thuc_phong_goi", { conversationId, endedBy: userId });
      return;
    }

    if (callState && !callState.isOutcomeEmitted) {
      callState.isOutcomeEmitted = true;
      await emitCallOutcomeMessage({
        conversationId,
        senderId: callState.initiatorId || callerId,
        callType: callState.callType || "video",
        outcome: "missed",
      });
    }

    endCallRoom(conversationId, userId);
  });

  socket.on("ket_thuc_goi", async ({ conversationId, userId }) => {
    if (!conversationId) return;

    const callState = activeCalls.get(conversationId);
    if (!callState) return;

    // Restore: Nếu là cuộc gọi nhóm, hành động "Kết thúc" của 1 người chỉ là "Rời đi"
    if (callState.isGroup) {
      callState.participants.delete(userId);
      socket.leave(`call:${conversationId}`);

      io.to(`call:${conversationId}`).emit("nguoi_dung_roi_goi", {
        conversationId,
        userId,
        participants: Array.from(callState.participants),
      });

      io.to(`conversation:${conversationId}`).emit("cap_nhat_trang_thai_goi_nhom", {
        conversationId,
        isCalling: callState.participants.size > 0,
        participantCount: callState.participants.size,
      });

      maybeCloseCallWhenOnlyOneLeft(conversationId, userId);
      return;
    }

    if (!callState.isOutcomeEmitted) {
      callState.isOutcomeEmitted = true;
      const outcome = callState.answeredAt ? "completed" : "missed";
      await emitCallOutcomeMessage({
        conversationId,
        senderId: callState.initiatorId || userId || "",
        callType: callState.callType,
        outcome,
        answeredAt: callState.answeredAt,
      });
    }

    endCallRoom(conversationId, userId || null);
    console.log(`Cuoc goi ket thuc tai phong ${conversationId}`);
  });

  const maybeCloseCallWhenOnlyOneLeft = (conversationId, endedBy = null) => {
    const callState = activeCalls.get(conversationId);
    if (!callState) return;

    // Đối với cuộc gọi nhóm: Chỉ kết thúc khi không còn ai (size === 0)
    // Đối với cuộc gọi 1-1: Kết thúc khi chỉ còn 1 người (size === 1)
    const shouldClose = callState.isGroup
      ? callState.participants.size === 0
      : callState.participants.size === 1;

    if (shouldClose) {
      const outcome = callState.answeredAt ? "completed" : "missed";
      if (!callState.isOutcomeEmitted) {
        callState.isOutcomeEmitted = true;
        emitCallOutcomeMessage({
          conversationId,
          senderId: callState.initiatorId || endedBy || "",
          callType: callState.callType,
          outcome,
          answeredAt: callState.answeredAt,
        });
      }
      endCallRoom(conversationId, endedBy);
    }
  };

  socket.on("trang_thai_camera", ({ conversationId, userId, isCameraOff }) => {
    if (!conversationId || !userId) return;
    io.to(`call:${conversationId}`).emit("thay_doi_trang_thai_camera", {
      conversationId,
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
      console.log(`Socket cua user ${userId} trong phong ${callRoomId} dang ngat ket noi (disconnecting).`);
      removeUserFromAllCalls(userId);
    }
  });

  socket.on("disconnect", async () => {
    const userId = socket.data.userId;
    if (!userId) {
      console.log("Socket disconnect: no userId");
      return;
    }

    // Kiểm tra xem user còn socket nào khác đang online không (ví dụ: tab CallPage hoặc tab chat khác)
    const activeSockets = await io.in(`user:${userId}`).fetchSockets();

    if (activeSockets.length === 0) {
      console.log(`User ${userId} da ngat ket noi hoan toan. Dang don dep cuoc goi...`);
      removeUserFromAllCalls(userId);
    } else {
      console.log(`User ${userId} ngat ket noi 1 socket, nhung van con ${activeSockets.length} socket khac hoat dong.`);
    }
  });
});

// ========== MESSAGE ROUTES ==========
app.use("/api", messageRoutes);

// ========== OTHER ROUTES ==========
app.use("/api", apiRoutes);

app.get("/", (req, res) => res.send("Chat Service dang chay..."));

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Chat Service dang chay tren port ${PORT}`);
});