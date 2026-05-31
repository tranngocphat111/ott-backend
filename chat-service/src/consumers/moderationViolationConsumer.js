const ContentViolationDetected = require("../dtos/ContentViolationDetected");
const Conversation = require("../models/Conversation");
const Message = require("../models/Message");
const User = require("../models/User");
const ParticipantService = require("../services/participantService");
const messageCacheService = require("../services/messageCacheService");
const logger = require("../utils/logger");

const MODERATION_EXCHANGE =
  process.env.MODERATION_RABBITMQ_EXCHANGE || "moderation.events";
const VIOLATION_ROUTING_KEY =
  process.env.MODERATION_VIOLATION_DETECTED_ROUTING_KEY ||
  "moderation.violation.detected";
const CHAT_VIOLATION_QUEUE =
  process.env.CHAT_MODERATION_VIOLATION_QUEUE ||
  "chat.moderation.violation.queue";
const CHAT_DLX =
  process.env.CHAT_MODERATION_DLX || "chat.moderation.dlx";
const CHAT_VIOLATION_DLQ =
  process.env.CHAT_MODERATION_VIOLATION_DLQ ||
  "chat.moderation.violation.dlq";

const MODERATION_PLACEHOLDER =
  "Tin nhắn đã bị ẩn do vi phạm tiêu chuẩn cộng đồng";

const safeParse = (buffer) => {
  try {
    return JSON.parse(buffer.toString());
  } catch {
    return null;
  }
};

const sanitizeAvatarValue = (value) => {
  const avatar = String(value || "").trim();
  if (!avatar) return "";
  if (/^data:image\//i.test(avatar)) return "";
  return avatar;
};

const normalizeEvidence = (event) =>
  event.evidence && typeof event.evidence === "object" ? event.evidence : {};

const normalizeImageIndex = (event) => {
  const evidence = normalizeEvidence(event);
  const evidenceIndex = Number(evidence.imageIndex);
  if (Number.isInteger(evidenceIndex) && evidenceIndex >= 0) {
    return evidenceIndex;
  }

  const requestParts = String(event.requestId || "").split(":");
  const requestIndex = Number(requestParts[requestParts.length - 1]);
  return Number.isInteger(requestIndex) && requestIndex >= 0 ? requestIndex : 0;
};

const getContentKeyAtIndex = (message, imageIndex) => {
  const content = Array.isArray(message.content)
    ? message.content
    : [message.content];
  return String(content[imageIndex] || "");
};

const buildModerationMeta = (event, currentMeta = null) => ({
  ...(currentMeta && typeof currentMeta === "object" ? currentMeta : {}),
  moderation_status: "rejected",
  moderation_violation_id: event.violationId,
  moderation_request_id: event.requestId,
  moderation_severity: event.severity || "HIGH",
  moderation_violation_type: event.violationType || "POLICY_VIOLATION",
  moderation_matched_labels: event.matchedLabels,
  moderation_detected_at: event.detectedAt,
});

const buildMediaWarning = (event, message) => {
  const evidence = normalizeEvidence(event);
  const imageIndex = normalizeImageIndex(event);
  const labels = Array.isArray(event.matchedLabels) ? event.matchedLabels : [];

  return {
    index: imageIndex,
    key: String(evidence.objectKey || getContentKeyAtIndex(message, imageIndex)),
    source: "rekognition",
    reason: labels.slice(0, 3).filter(Boolean).join(", ") || "moderation_label",
    severity: event.severity || "HIGH",
    violation_id: event.violationId,
    request_id: event.requestId,
    detected_at: event.detectedAt,
  };
};

const mergeMediaWarning = (warnings, nextWarning) => {
  const currentWarnings = Array.isArray(warnings) ? warnings : [];
  const exists = currentWarnings.some(
    (warning) =>
      warning?.violation_id === nextWarning.violation_id ||
      warning?.request_id === nextWarning.request_id,
  );

  if (exists) {
    return currentWarnings;
  }

  return [...currentWarnings, nextWarning];
};

const getPayloadMessage = async (message) => {
  const sender = await User.findOne({ user_id: message.sender_id })
    .select("name avatar")
    .lean();

  return {
    ...message.toObject(),
    sender_name: sender?.name || "",
    sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
  };
};

const updateConversationLastMessage = async (message) => {
  await Conversation.updateOne(
    {
      _id: message.conversation_id,
      "last_message.msg_id": message.msg_id,
    },
    {
      $set: {
        "last_message.content": MODERATION_PLACEHOLDER,
        "last_message.type": "text",
        updatedAt: new Date(),
      },
    },
  );
};

const emitToConversationParticipants = async (io, conversationId, event, payload) => {
  const participants = await ParticipantService.getJoinedParticipants(conversationId);

  participants.forEach((participant) => {
    io.to(`user:${participant.user_id}`).emit(event, payload);
  });
};

const applyTextViolationToMessage = async (event, io) => {
  const message = await Message.findOne({ msg_id: event.contentRefId });
  if (!message) {
    logger.warn(
      `[moderation] text violation target message not found: msgId=${event.contentRefId}, violationId=${event.violationId}`,
    );
    return;
  }

  const currentMeta =
    message.system_meta && typeof message.system_meta === "object"
      ? message.system_meta
      : {};

  if (
    currentMeta.moderation_violation_id === event.violationId ||
    currentMeta.moderation_request_id === event.requestId
  ) {
    logger.info(
      `[moderation] duplicate text violation ignored: msgId=${message.msg_id}, violationId=${event.violationId}`,
    );
    return;
  }

  if (message.is_deleted) {
    logger.info(`[moderation] text violation ignored for deleted message: msgId=${message.msg_id}`);
    return;
  }

  message.system_meta = buildModerationMeta(event, currentMeta);
  message.is_revoked = true;
  message.content = [MODERATION_PLACEHOLDER];
  message.reactions = [];
  message.is_pinned = false;
  message.pinned_at = null;
  message.pinned_by = null;

  const savedMessage = await message.save();
  const payload = {
    ...(await getPayloadMessage(savedMessage)),
    is_revoked: true,
    is_deleted: !!savedMessage.is_deleted,
    reactions: [],
  };

  await Promise.allSettled([
    messageCacheService.updateMessage(
      savedMessage.conversation_id,
      savedMessage.msg_id,
      payload,
    ),
    updateConversationLastMessage(savedMessage),
  ]);

  await emitToConversationParticipants(
    io,
    savedMessage.conversation_id,
    "tin_nhan_thu_hoi",
    payload,
  );

  logger.warn(
    `[moderation] text message auto-hidden: msgId=${savedMessage.msg_id}, violationId=${event.violationId}, labels=${event.matchedLabels.join(",")}`,
  );
};

const applyImageViolationToMessage = async (event, io) => {
  const evidence = normalizeEvidence(event);
  const targetMessageId = String(evidence.messageId || event.contentRefId || "");
  const message = await Message.findOne({ msg_id: targetMessageId });

  if (!message) {
    logger.warn(
      `[moderation] image violation target message not found: msgId=${targetMessageId}, violationId=${event.violationId}`,
    );
    return;
  }

  if (message.is_deleted || message.is_revoked) {
    logger.info(`[moderation] image violation ignored for inactive message: msgId=${message.msg_id}`);
    return;
  }

  const currentMeta =
    message.system_meta && typeof message.system_meta === "object"
      ? message.system_meta
      : {};
  const nextWarning = buildMediaWarning(event, message);
  const nextWarnings = mergeMediaWarning(currentMeta.media_warnings, nextWarning);

  if (nextWarnings.length === (currentMeta.media_warnings || []).length) {
    logger.info(
      `[moderation] duplicate image violation ignored: msgId=${message.msg_id}, violationId=${event.violationId}`,
    );
    return;
  }

  message.system_meta = {
    ...buildModerationMeta(event, currentMeta),
    media_policy_status: "rejected",
    media_warnings: nextWarnings,
  };
  message.is_revoked = true;
  message.content = [MODERATION_PLACEHOLDER];
  message.reactions = [];
  message.is_pinned = false;
  message.pinned_at = null;
  message.pinned_by = null;

  const savedMessage = await message.save();
  const payload = {
    ...(await getPayloadMessage(savedMessage)),
    is_revoked: true,
    is_deleted: !!savedMessage.is_deleted,
    reactions: [],
  };

  await Promise.allSettled([
    messageCacheService.updateMessage(
      savedMessage.conversation_id,
      savedMessage.msg_id,
      payload,
    ),
    updateConversationLastMessage(savedMessage),
  ]);

  await emitToConversationParticipants(
    io,
    savedMessage.conversation_id,
    "tin_nhan_thu_hoi",
    payload,
  );

  logger.warn(
    `[moderation] image message auto-hidden: msgId=${savedMessage.msg_id}, imageIndex=${nextWarning.index}, violationId=${event.violationId}, labels=${event.matchedLabels.join(",")}`,
  );
};

const applyViolationToMessage = async (event, io) => {
  if (event.isChatTextViolation()) {
    await applyTextViolationToMessage(event, io);
    return;
  }

  if (event.isChatImageViolation()) {
    await applyImageViolationToMessage(event, io);
    return;
  }

  logger.info(
    `[moderation] ignored unsupported violation: sourceService=${event.sourceService}, contentType=${event.contentType}`,
  );
};

const assertTopology = async (channel) => {
  await channel.assertExchange(MODERATION_EXCHANGE, "direct", {
    durable: true,
  });
  await channel.assertExchange(CHAT_DLX, "direct", { durable: true });
  await channel.assertQueue(CHAT_VIOLATION_QUEUE, {
    durable: true,
    arguments: {
      "x-dead-letter-exchange": CHAT_DLX,
      "x-dead-letter-routing-key": CHAT_VIOLATION_DLQ,
    },
  });
  await channel.assertQueue(CHAT_VIOLATION_DLQ, { durable: true });
  await channel.bindQueue(
    CHAT_VIOLATION_QUEUE,
    MODERATION_EXCHANGE,
    VIOLATION_ROUTING_KEY,
  );
  await channel.bindQueue(CHAT_VIOLATION_DLQ, CHAT_DLX, CHAT_VIOLATION_DLQ);
};

const initModerationViolationConsumer = async (channel, io) => {
  await assertTopology(channel);

  await channel.consume(
    CHAT_VIOLATION_QUEUE,
    async (message) => {
      if (!message) return;

      const payload = safeParse(message.content);
      if (!payload) {
        channel.nack(message, false, false);
        return;
      }

      try {
        const event = ContentViolationDetected.fromPayload(payload);
        await applyViolationToMessage(event, io);
        channel.ack(message);
      } catch (error) {
        logger.error("[moderation] failed to apply violation event", error);
        channel.nack(message, false, false);
      }
    },
    { noAck: false },
  );

  console.log(" [OK] ModerationViolationConsumer: chat auto-hide ready");
};

module.exports = {
  initModerationViolationConsumer,
};
