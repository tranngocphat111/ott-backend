const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");

exports.addParticipant = async ({ conversationId, userId, role }) => {
  const existing = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (existing) {
    return existing;
  }

  const newMember = new Participant({
    conversation_id: conversationId,
    user_id: userId,
    roles: role,
  });

  return await newMember.save();
};

/**
 * Lấy danh sách cuộc hội thoại của user, áp dụng cơ chế soft-delete kiểu Zalo:
 *   - Hiện: chưa bao giờ xóa (deleted_msg_id = "0")
 *   - Hiện: có tin nhắn mới sau lần xóa (last_message.msg_id > deleted_msg_id)
 *   - Ẩn:  không có tin nhắn nào mới hơn deleted_msg_id
 */
exports.getConversationsByUserId = async (userId) => {
  const participants = await Participant.find({ user_id: userId })
    .populate("conversation_id")
    .sort({ updatedAt: -1 });

  return participants.filter((p) => {
    const conversation = p.conversation_id;
    if (!conversation) return false;

    const lastMsgId = conversation.last_message?.msg_id;
    const deletedMsgId = p.deleted_msg_id || "0";

    // Chưa bao giờ xóa → hiển thị
    if (deletedMsgId === "0") return true;

    // Có tin nhắn mới hơn thời điểm xóa → hiển thị lại
    if (lastMsgId) {
      return BigInt(lastMsgId) > BigInt(deletedMsgId);
    }

    // Đã xóa, cuộc hội thoại không còn tin nhắn mới → ẩn
    return false;
  });
};

exports.getParticipants = async (conversationId) => {
  return await Participant.find({ conversation_id: conversationId });
};

exports.updateLastRead = async (conversationId, userId, msgId) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    {
      last_read_message_id: msgId,
      last_read_at: new Date(),
    },
    { new: true }
  );
};

exports.updateConversationCategory = async (conversationId, userId, categoryId) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    { "settings.category_id": categoryId },
    { new: true }
  );
};

exports.updateNotificationStatus = async (conversationId, userId, status, muteUntil) => {
  const updateData = { "settings.notification_status": status };

  if (muteUntil) {
    updateData["settings.mute_until"] = muteUntil;
  }

  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    updateData,
    { new: true }
  );
};

exports.updatePinStatus = async (conversationId, userId, isPinned) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    {
      "settings.is_pinned": isPinned,
      "settings.pinned_at": isPinned ? new Date() : null,
    },
    { new: true }
  );
};

/**
 * Xóa cuộc hội thoại theo cơ chế soft-delete của Zalo:
 * Đặt deleted_msg_id = msg_id của tin nhắn cuối trong cuộc hội thoại.
 * Cuộc hội thoại sẽ tự hiển thị lại khi có tin nhắn mới hơn deleted_msg_id.
 */
exports.deleteConversation = async (conversationId, userId) => {
  const conversation = await Conversation.findById(conversationId);

  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (!conversation.last_message?.msg_id) {
    throw new Error("Không thể xóa cuộc hội thoại chưa có tin nhắn nào");
  }

  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    { deleted_msg_id: conversation.last_message.msg_id },
    { new: true }
  );
};

exports.getParticipant = async (conversationId, userId) => {
  return await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });
};
