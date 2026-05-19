const aiService = require("../services/aiService");
const messageService = require("../services/messageService");
const participantService = require("../services/participantService");
const User = require("../models/User");
const fs = require("fs/promises");

const SMART_REPLY_TYPES = new Set(["text"]);
const SUMMARY_TYPES = new Set(["text"]);

const toPositiveInt = (value, fallback, max) => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(Math.floor(parsed), max);
};

const getRequesterId = (req) =>
  req.query.userId ||
  req.body?.userId ||
  req.headers["x-user-id"] ||
  req.headers["x-current-user-id"] ||
  "";

const extractContent = (message) => {
  if (!message) return "";

  if (message.type === "poll") {
    const question = message.poll_question || "";
    const options = Array.isArray(message.poll_options)
      ? message.poll_options.map((option) => option?.name).filter(Boolean).join(", ")
      : "";
    return [question, options ? `Lựa chọn: ${options}` : ""].filter(Boolean).join(" - ");
  }

  const content = message.content;
  if (Array.isArray(content)) return content.filter(Boolean).join(" ");
  if (typeof content === "string") return content;
  if (content && typeof content === "object") {
    return content.text || content.content || "";
  }
  return "";
};

const isVisibleMessage = (message) =>
  message &&
  !message.is_deleted &&
  !message.is_revoked &&
  !message.deleted_at;

const isVisibleToRequester = (message, requesterId) => {
  if (!isVisibleMessage(message)) return false;
  if (!requesterId) return true;

  const deletedFor = Array.isArray(message.deleted_for)
    ? message.deleted_for.map((userId) => String(userId))
    : [];

  return !deletedFor.includes(String(requesterId));
};

const getSenderNameMap = async (messages, requesterId) => {
  const senderIds = [
    ...new Set(
      messages
        .map((message) => String(message.sender_id || message.senderId || ""))
        .concat(requesterId ? [String(requesterId)] : [])
        .filter(Boolean),
    ),
  ];

  if (!senderIds.length) return new Map();

  const users = await User.find({ user_id: { $in: senderIds } })
    .select("user_id name")
    .lean();

  return new Map(users.map((user) => [String(user.user_id), user.name || "Người dùng"]));
};

const buildContextMessages = async (rawMessages, { requesterId, allowedTypes }) => {
  const visibleMessages = (rawMessages || [])
    .filter((message) => isVisibleToRequester(message, requesterId))
    .filter((message) => !allowedTypes || allowedTypes.has(message.type))
    .filter((message) => extractContent(message).trim());

  const senderNameMap = await getSenderNameMap(visibleMessages, requesterId);

  return [...visibleMessages].reverse().map((message) => ({
    senderId: String(message.sender_id || ""),
    senderName:
      senderNameMap.get(String(message.sender_id || "")) ||
      message.sender_name ||
      "Người dùng",
    type: message.type || "text",
    content: extractContent(message),
    createdAt: message.createdAt,
  }));
};

const ensureConversationAccess = async (conversationId, userId) => {
  if (!userId) return;

  const participant = await participantService.getParticipant(conversationId, userId);
  if (!participant || participant.status !== "joined") {
    const error = new Error("Bạn không có quyền dùng AI cho hội thoại này.");
    error.statusCode = 403;
    throw error;
  }
};

const sendControllerError = (res, error, fallbackStatus = 500) => {
  const status = error.statusCode || fallbackStatus;
  res.status(status).json({
    error: error.message || "Không thể xử lý yêu cầu AI lúc này.",
  });
};

const shouldReturnDetailed = (req) =>
  String(req.query.detailed || req.query.includeMeta || "").toLowerCase() === "true";

exports.getSmartReplies = async (req, res) => {
  try {
    const { conversationId } = req.query;
    const requesterId = getRequesterId(req);

    if (!conversationId) {
      return res.status(400).json({ error: "conversationId is required" });
    }
    if (!requesterId) {
      return res.status(400).json({ error: "userId is required" });
    }

    await ensureConversationAccess(conversationId, requesterId);

    const limit = toPositiveInt(req.query.limit, 12, 20);
    const messages = await messageService.getMessages(conversationId, {
      limit,
      types: SMART_REPLY_TYPES,
      userId: requesterId,
    });
    const contextMessages = await buildContextMessages(messages, {
      requesterId,
      allowedTypes: SMART_REPLY_TYPES,
    });

    if (!contextMessages.length) {
      const empty = { replies: [], suggestions: [], meta: { source: "empty" } };
      return res.json(shouldReturnDetailed(req) ? empty : []);
    }

    const senderNameMap = await getSenderNameMap(contextMessages, requesterId);
    const result = await aiService.generateSmartReplies(contextMessages, {
      currentUserId: requesterId,
      currentUserName: senderNameMap.get(String(requesterId || "")) || "",
      conversationType: req.query.conversationType || "chat",
    });

    return res.json(shouldReturnDetailed(req) ? result : result.replies || []);
  } catch (error) {
    console.error("Smart Reply Controller Error:", error.message);
    return sendControllerError(res, error);
  }
};

exports.summarizeConversation = async (req, res) => {
  try {
    const { conversationId } = req.query;
    const requesterId = getRequesterId(req);

    if (!conversationId) {
      return res.status(400).json({ error: "conversationId is required" });
    }
    if (!requesterId) {
      return res.status(400).json({ error: "userId is required" });
    }

    await ensureConversationAccess(conversationId, requesterId);

    const limit = toPositiveInt(req.query.limit, 60, 120);
    const messages = await messageService.getMessages(conversationId, {
      limit,
      types: SUMMARY_TYPES,
      userId: requesterId,
    });
    const contextMessages = await buildContextMessages(messages, {
      requesterId,
      allowedTypes: SUMMARY_TYPES,
    });

    if (!contextMessages.length) {
      return res.json({
        summary: "Hội thoại chưa có đủ dữ liệu để tóm tắt.",
        highlights: [],
        actionItems: [],
        questions: [],
        sentiment: "neutral",
      });
    }

    const senderNameMap = await getSenderNameMap(contextMessages, requesterId);
    const result = await aiService.summarizeChat(contextMessages, {
      currentUserId: requesterId,
      currentUserName: senderNameMap.get(String(requesterId || "")) || "",
    });

    return res.json(result);
  } catch (error) {
    console.error("Summarization Controller Error:", error.message);
    return sendControllerError(res, error);
  }
};

exports.translateText = async (req, res) => {
  try {
    const { text, targetLang } = req.body || {};
    if (!text || !String(text).trim()) {
      return res.status(400).json({ error: "text is required" });
    }

    const result = await aiService.translateMessage(text, targetLang);
    return res.json(result);
  } catch (error) {
    console.error("Translation Controller Error:", error.message);
    return sendControllerError(res, error);
  }
};

exports.transcribeVoice = async (req, res) => {
  const tempPath = req.file?.path;

  try {
    if (!req.file) {
      return res.status(400).json({ error: "Audio file is required" });
    }

    const transcription = await aiService.transcribeAudio(req.file.path);
    return res.json({ text: transcription });
  } catch (error) {
    console.error("Transcription Controller Error:", error.message);
    return sendControllerError(res, error, error.message?.includes("GROQ_API_KEY") ? 503 : 500);
  } finally {
    if (tempPath) {
      await fs.unlink(tempPath).catch((unlinkError) => {
        console.error("Error deleting temp audio file:", unlinkError.message);
      });
    }
  }
};
