const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");

exports.addParticipant = async ({ conversationId, userId, role, addedBy }) => {
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
    added_by: addedBy,
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

exports.updateMemberNickname = async (
  conversationId,
  targetUserId,
  requesterUserId,
  nickname,
) => {
  const requester = await Participant.findOne({
    conversation_id: conversationId,
    user_id: requesterUserId,
  });

  if (!requester) {
    throw new Error("Bạn không thuộc cuộc hội thoại này");
  }

  const target = await Participant.findOne({
    conversation_id: conversationId,
    user_id: targetUserId,
  });

  if (!target) {
    throw new Error("Thành viên không tồn tại trong cuộc hội thoại");
  }

  const trimmedNickname = String(nickname || "").trim();
  target.nickname = trimmedNickname || null;
  await target.save();

  return {
    success: true,
    conversationId,
    userId: targetUserId,
    nickname: target.nickname || "",
  };
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

// Get all members of a conversation with user details
exports.getConversationMembers = async (conversationId) => {
  const User = require("../models/User");
  
  const participants = await Participant.find({ conversation_id: conversationId });
  
  // Get user details for each participant
  const membersWithDetails = await Promise.all(
    participants.map(async (p) => {
      const user = await User.findOne({ user_id: p.user_id }).lean();
      return {
        _id: p._id,
        user_id: p.user_id,
        roles: p.roles,
        joined_at: p.joined_at,
        added_by: p.added_by,
        nickname: p.nickname,
        user: user ? {
          name: user.name,
          avatar: user.avatar,
          is_online: user.is_online,
          last_active_at: user.last_active_at,
        } : null,
      };
    })
  );

  return membersWithDetails;
};

// Leave group (remove participant from conversation)
exports.leaveGroup = async (conversationId, userId) => {
  const conversation = await Conversation.findById(conversationId);
  
  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể rời khỏi nhóm chat");
  }

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Bạn không phải là thành viên của nhóm này");
  }

  // Remove participant
  await Participant.deleteOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  // Update member count
  await Conversation.findByIdAndUpdate(conversationId, {
    $inc: { member_count: -1 },
  });

  return { success: true, conversationId, userId };
};

// Update member role (owner only)
exports.updateMemberRole = async (conversationId, userId, newRole, adminId) => {
  const conversation = await Conversation.findById(conversationId);
  
  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể cập nhật vai trò trong nhóm chat");
  }

  if (String(conversation.created_by) !== String(adminId)) {
    throw new Error("Chỉ trưởng nhóm mới có quyền thay đổi vai trò thành viên");
  }

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Người dùng không phải là thành viên của nhóm này");
  }

  // Cannot change own role
  if (userId === adminId) {
    throw new Error("Bạn không thể thay đổi vai trò của chính mình");
  }

  // Update role
  participant.roles = newRole;
  await participant.save();

  return { success: true, conversationId, userId, newRole };
};

// Remove member from group (owner only)
exports.removeMember = async (conversationId, userId, adminId) => {
  const conversation = await Conversation.findById(conversationId);
  
  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể xóa thành viên khỏi nhóm chat");
  }

  if (String(conversation.created_by) !== String(adminId)) {
    throw new Error("Chỉ trưởng nhóm mới có quyền xóa thành viên");
  }

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Người dùng không phải là thành viên của nhóm này");
  }

  // Cannot remove owner
  if (String(userId) === String(conversation.created_by)) {
    throw new Error("Không thể xóa trưởng nhóm");
  }

  // Remove participant
  await Participant.deleteOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  // Update member count
  await Conversation.findByIdAndUpdate(conversationId, {
    $inc: { member_count: -1 },
  });

  return { success: true, conversationId, userId };
};
