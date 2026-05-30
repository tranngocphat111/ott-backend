const Message = require("../models/Message");
const User = require("../models/User");
const ParticipantService = require("../services/participantService");
const {
  QUEUES,
  ROUTING_KEYS,
  publishMessageStatusChanged,
} = require("../events/chatEvents");

const safeParse = (buffer) => {
  try {
    return JSON.parse(buffer.toString());
  } catch {
    return null;
  }
};

const isCursorAtLeast = (cursor, msgId) => {
  try {
    return BigInt(String(cursor || "0")) >= BigInt(String(msgId || "0"));
  } catch {
    return String(cursor || "0") >= String(msgId || "0");
  }
};

const sanitizeAvatarValue = (value) => {
  const avatar = String(value || "").trim();
  if (!avatar) return "";
  if (/^data:image\//i.test(avatar)) return "";
  return avatar;
};

const getFileNameFromKey = (key) => {
  const rawName =
    String(key || "")
      .split("/")
      .pop() || "File";
  const match = rawName.match(/^[a-f0-9]+_(.+)$/i);
  return match ? match[1] : rawName;
};

const buildReplyPreview = (message, senderName = "") => {
  if (!message) return null;

  const rawContent = Array.isArray(message.content)
    ? message.content[0] || ""
    : message.content || "";
  const mediaUrls = Array.isArray(message.content)
    ? message.content.filter(Boolean).map((item) => String(item))
    : rawContent
      ? [String(rawContent)]
      : [];
  const isUrlLike = /^(https?:\/\/|www\.)/i.test(rawContent);
  const mediaUrl = rawContent && !isUrlLike ? rawContent : "";
  const fileName =
    message.type === "file" ||
    message.type === "video" ||
    message.type === "audio"
      ? getFileNameFromKey(rawContent)
      : "";

  let preview = rawContent;
  if (message.type === "file") preview = fileName || "[Tệp tin]";
  if (message.type === "audio") preview = fileName || "[Âm thanh]";

  return {
    msg_id: message.msg_id,
    sender_id: message.sender_id,
    sender_name: senderName || message.sender_name || "",
    type: message.type,
    content: preview.length > 120 ? `${preview.substring(0, 120)}...` : preview,
    raw_content: rawContent,
    file_name: fileName,
    url: mediaUrl || rawContent,
    media_urls: message.type === "image" ? mediaUrls : undefined,
    media_count: message.type === "image" ? mediaUrls.length : undefined,
    is_deleted: !!message.is_deleted,
    is_revoked: !!message.is_revoked,
    poll_question: message.type === "poll" ? message.poll_question : undefined,
  };
};

const getMessageForDelivery = async (conversationId, msgId) => {
  const message = await Message.findOne({
    conversation_id: conversationId,
    msg_id: msgId,
  }).lean();

  if (!message) return null;

  const [sender, replyMessage] = await Promise.all([
    User.findOne({ user_id: message.sender_id }).select("name avatar").lean(),
    message.reply_to_msg_id
      ? Message.findOne({
          conversation_id: conversationId,
          msg_id: message.reply_to_msg_id,
        }).lean()
      : null,
  ]);

  const replySender = replyMessage
    ? await User.findOne({ user_id: replyMessage.sender_id })
        .select("name")
        .lean()
    : null;

  return {
    ...message,
    sender_name: sender?.name || "",
    sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
    reply_to: buildReplyPreview(replyMessage, replySender?.name || ""),
  };
};

const buildStatusPayload = async ({
  conversationId,
  msgId,
  senderId,
  changedUserId,
  receiptType,
  participant,
}) => {
  const participants = await ParticipantService.getJoinedParticipants(
    conversationId,
  );
  const recipients = participants.filter(
    (item) => String(item.user_id) !== String(senderId),
  );
  const deliveredCount = recipients.filter((item) =>
    isCursorAtLeast(item.last_delivered_message_id, msgId),
  ).length;
  const seenCount = recipients.filter((item) =>
    isCursorAtLeast(item.last_read_message_id, msgId),
  ).length;
  const status =
    seenCount > 0 ? "seen" : deliveredCount > 0 ? "delivered" : "sent";

  return {
    conversationId: String(conversationId),
    msgId: String(msgId),
    senderId: String(senderId),
    userId: String(changedUserId),
    changedUserId: String(changedUserId),
    receiptType,
    status,
    deliveredCount,
    seenCount,
    recipientCount: recipients.length,
    participant: participant
      ? {
          user_id: participant.user_id,
          conversation_id: String(participant.conversation_id),
          last_delivered_message_id:
            participant.last_delivered_message_id || "0",
          last_delivered_at: participant.last_delivered_at || null,
          last_read_message_id: participant.last_read_message_id || "0",
          last_read_at: participant.last_read_at || null,
        }
      : null,
  };
};

const handleMessageCreated = async (io, payload) => {
  const conversationId = payload?.conversationId;
  const msgId = payload?.msgId;
  const senderId = payload?.senderId;
  if (!conversationId || !msgId || !senderId) return;

  const message = payload.message || (await getMessageForDelivery(conversationId, msgId));
  if (!message) return;

  const participants = await ParticipantService.getJoinedParticipants(
    conversationId,
  );

  participants.forEach((participant) => {
    io.to(`user:${participant.user_id}`).emit("tin_nhan", message);
  });
};

const handleReceipt = async (payload, routingKey) => {
  const conversationId = payload?.conversationId;
  const userId = payload?.userId;
  const msgId = payload?.msgId;
  if (!conversationId || !userId || !msgId) return;

  const isSeen = routingKey === ROUTING_KEYS.MESSAGE_SEEN;
  const participant = isSeen
    ? await ParticipantService.updateLastRead(conversationId, userId, msgId)
    : await ParticipantService.updateLastDelivered(conversationId, userId, msgId);
  if (!participant) return;
  if (participant.$locals?.cursorChanged === false) return;

  const message = await Message.findOne({
    conversation_id: conversationId,
    msg_id: msgId,
  })
    .select("sender_id msg_id")
    .lean();

  if (!message) return;

  const statusPayload = await buildStatusPayload({
    conversationId,
    msgId,
    senderId: message.sender_id,
    changedUserId: userId,
    receiptType: isSeen ? "seen" : "delivered",
    participant,
  });

  await publishMessageStatusChanged(statusPayload);
};

const handleStatusChanged = async (io, payload) => {
  if (!payload?.conversationId || !payload?.msgId || !payload?.senderId) return;

  io.to(`user:${payload.senderId}`).emit("message_status_changed", payload);

  const participants = await ParticipantService.getJoinedParticipants(
    payload.conversationId,
  );

  participants.forEach((participant) => {
    io.to(`user:${participant.user_id}`).emit("participant_cursor_changed", {
      conversationId: payload.conversationId,
      userId: payload.changedUserId || payload.userId,
      participant: payload.participant,
      msgId: payload.msgId,
      receiptType: payload.receiptType,
    });
  });

  if (payload.receiptType === "seen" && payload.changedUserId) {
    io.to(`user:${payload.changedUserId}`).emit("conversation_read_synced", {
      conversationId: payload.conversationId,
      userId: payload.changedUserId,
      msgId: payload.msgId,
      receiptType: "seen",
      participant: payload.participant,
    });
  }
};

const consumeJson = (channel, queue, handler) =>
  channel.consume(
    queue,
    async (message) => {
      if (!message) return;

      const payload = safeParse(message.content);
      if (!payload) {
        channel.ack(message);
        return;
      }

      try {
        await handler(payload, message.fields.routingKey);
        channel.ack(message);
      } catch (error) {
        console.error(
          `[chat-events] Failed processing ${queue}:`,
          error.message,
        );
        channel.nack(message, false, true);
      }
    },
    { noAck: false },
  );

const initChatMessageConsumers = async (channel, io) => {
  await channel.prefetch(20);

  await consumeJson(channel, QUEUES.DELIVERY, (payload) =>
    handleMessageCreated(io, payload),
  );
  await consumeJson(channel, QUEUES.RECEIPT, handleReceipt);
  await consumeJson(channel, QUEUES.STATUS, (payload) =>
    handleStatusChanged(io, payload),
  );

  console.log(" [✓] ChatMessageConsumers: delivery, receipt, status ready");
};

module.exports = {
  initChatMessageConsumers,
};
