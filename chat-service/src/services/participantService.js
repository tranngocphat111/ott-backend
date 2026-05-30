const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");
const mongoose = require("mongoose");

const normalizeMessageId = (value) => {
  const normalized = String(value || "0").trim();
  return /^\d+$/.test(normalized) ? normalized : "0";
};

const isMessageIdAfter = (left, right) => {
  const safeLeft = normalizeMessageId(left);
  const safeRight = normalizeMessageId(right);
  try {
    return BigInt(safeLeft) > BigInt(safeRight);
  } catch {
    return safeLeft > safeRight;
  }
};

const isObjectIdLike = (value) => {
  const normalized = String(value || "").trim();
  return mongoose.Types.ObjectId.isValid(normalized);
};

const assertGroupManager = async (conversationId, requesterId) => {
  const conversation = await Conversation.findById(conversationId);
  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể thực hiện thao tác này trong nhóm chat");
  }

  const isOwner = String(conversation.created_by) === String(requesterId);
  if (isOwner) {
    return { conversation, isOwner: true };
  }

  const requester = await Participant.findOne({
    conversation_id: conversationId,
    user_id: requesterId,
  });

  if (!requester || requester.roles !== "admin") {
    throw new Error("Chỉ trưởng nhóm hoặc phó nhóm mới có quyền thực hiện");
  }

  return { conversation, isOwner: false };
};

exports.ensureSelfConversation = async (userId) => {
  if (!userId) return null;

  const selfConversation = await Conversation.findOneAndUpdate(
    {
      is_self_conversation: true,
      self_owner_id: userId,
      is_deleted: false,
    },
    {
      $setOnInsert: {
        type: "private",
        name: "My Documents",
        avatar: "",
        created_by: userId,
        background: "",
        is_self_conversation: true,
        self_owner_id: userId,
      },
      $set: {
        member_count: 1,
      },
    },
    {
      upsert: true,
      new: true,
    },
  );

  const participant = await Participant.findOneAndUpdate(
    {
      conversation_id: selfConversation._id,
      user_id: userId,
    },
    {
      $setOnInsert: {
        roles: "admin",
        added_by: userId,
      },
    },
    {
      upsert: true,
      new: true,
    },
  );

  if (!participant?.settings?.is_pinned) {
    await Participant.findByIdAndUpdate(participant._id, {
      "settings.is_pinned": true,
      "settings.pinned_at": new Date(),
    });
  }

  return { selfConversation, participant };
};

exports.addParticipant = async ({ conversationId, userId, role, addedBy, lastMsgId = "0", status = "joined" }) => {
  const existing = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (existing) {
    existing.roles = role || existing.roles;
    existing.added_by = addedBy || existing.added_by;
    existing.joined_at = new Date();
    existing.status = status || existing.status;
    // Khi thêm lại thành viên đã bị xóa/đuổi, vẫn áp dụng logic ẩn tin nhắn cũ
    existing.deleted_msg_id = lastMsgId;
    return await existing.save();
  }

  const newMember = new Participant({
    conversation_id: conversationId,
    user_id: userId,
    roles: role,
    added_by: addedBy,
    deleted_msg_id: lastMsgId,
    status: status,
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
    .sort({ updatedAt: -1 })
    .lean();

  return participants.filter((p) => {
    const conversation = p.conversation_id;
    if (!conversation) return false;

    const lastMsgId = conversation.last_message?.msg_id;
    const deletedMsgId = p.deleted_msg_id || "0";

    // Chưa bao giờ xóa → hiển thị
    if (deletedMsgId === "0") return true;

    // Có tin nhắn mới hơn thời điểm xóa → hiển thị lại
    if (lastMsgId) {
      return isMessageIdAfter(lastMsgId, deletedMsgId);
    }

    // Đã xóa, cuộc hội thoại không còn tin nhắn mới → ẩn
    return false;
  });
};

exports.getParticipants = async (conversationId) => {
  return await Participant.find({
    conversation_id: conversationId,
    status: { $in: ["joined", "invited"] },
    "settings.removed_from_group_at": null,
  });
};

// Only joined participants — used for message emission (invited users should NOT see messages)
exports.getJoinedParticipants = async (conversationId) => {
  return await Participant.find({
    conversation_id: conversationId,
    status: "joined",
    "settings.removed_from_group_at": null,
  });
};

exports.getParticipantsIncludingRemoved = async (conversationId) => {
  return await Participant.find({ conversation_id: conversationId });
};

const maxMessageId = (left = "0", right = "0") => {
  try {
    return BigInt(String(left || "0")) >= BigInt(String(right || "0"))
      ? String(left || "0")
      : String(right || "0");
  } catch {
    return String(right || "0");
  }
};

const shouldAdvanceMessageId = (current = "0", next = "0") => {
  try {
    return BigInt(String(next || "0")) > BigInt(String(current || "0"));
  } catch {
    return String(next || "0") !== String(current || "0");
  }
};

exports.updateLastDelivered = async (conversationId, userId, msgId) => {
  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) return null;

  if (shouldAdvanceMessageId(participant.last_delivered_message_id, msgId)) {
    participant.last_delivered_message_id = String(msgId || "0");
    participant.last_delivered_at = new Date();
    const saved = await participant.save();
    saved.$locals = saved.$locals || {};
    saved.$locals.cursorChanged = true;
    return saved;
  }

  participant.$locals = participant.$locals || {};
  participant.$locals.cursorChanged = false;
  return participant;
};

exports.updateLastRead = async (conversationId, userId, msgId) => {
  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) return null;

  const now = new Date();
  const nextMsgId = String(msgId || "0");
  const hadDeliveredAt = !!participant.last_delivered_at;
  const readAdvanced = shouldAdvanceMessageId(
    participant.last_read_message_id,
    nextMsgId,
  );
  const deliveredAdvanced = shouldAdvanceMessageId(
    participant.last_delivered_message_id,
    nextMsgId,
  );

  if (readAdvanced) {
    participant.last_read_message_id = nextMsgId;
    participant.last_read_at = now;
  }

  if (deliveredAdvanced) {
    participant.last_delivered_message_id = maxMessageId(
      participant.last_delivered_message_id,
      nextMsgId,
    );
    participant.last_delivered_at = participant.last_delivered_at || now;
  } else if (!participant.last_delivered_at) {
    participant.last_delivered_at = now;
  }

  const cursorChanged = readAdvanced || deliveredAdvanced || !hadDeliveredAt;

  if (!cursorChanged) {
    participant.$locals = participant.$locals || {};
    participant.$locals.cursorChanged = false;
    return participant;
  }

  const saved = await participant.save();
  saved.$locals = saved.$locals || {};
  saved.$locals.cursorChanged = true;
  return saved;
};

exports.updateConversationCategory = async (conversationId, userId, categoryId) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    { "settings.category_id": categoryId },
    { new: true }
  );
};

exports.updateNotificationStatus = async (conversationId, userId, status, muteUntil) => {
  const updateData = {
    "settings.notification_status": status,
    "settings.mute_until": status === "mute" ? (muteUntil || null) : null,
  };

  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    { $set: updateData },
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
  
  const participants = await Participant.find({
    conversation_id: conversationId,
    "settings.removed_from_group_at": null,
  });
  
  // Get user details for each participant
  const membersWithDetails = await Promise.all(
    participants.map(async (p) => {
      // Try to find by UUID first, then by ObjectId
      let user = await User.findOne({ user_id: p.user_id }).lean();
      if (!user && isObjectIdLike(p.user_id)) {
        user = await User.findById(p.user_id).lean();
      }
      return {
        _id: p._id,
        user_id: p.user_id,
        roles: p.roles,
        joined_at: p.joined_at,
        last_delivered_message_id: p.last_delivered_message_id || "0",
        last_delivered_at: p.last_delivered_at || null,
        last_read_message_id: p.last_read_message_id || "0",
        last_read_at: p.last_read_at || null,
        added_by: p.added_by,
        status: p.status,
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

  if (String(conversation.created_by) === String(userId)) {
    throw new Error("Trưởng nhóm không thể rời nhóm. Vui lòng giải tán nhóm");
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

// Update member role (owner or admin)
exports.updateMemberRole = async (conversationId, userId, newRole, adminId) => {
  const { conversation, isOwner } = await assertGroupManager(conversationId, adminId);

  if (!isOwner) {
    throw new Error("Chỉ trưởng nhóm mới có quyền phân quyền");
  }

  if (!["admin", "user"].includes(String(newRole))) {
    throw new Error("Vai trò không hợp lệ");
  }

  if (String(userId) === String(conversation.created_by)) {
    throw new Error("Không thể thay đổi vai trò của trưởng nhóm");
  }

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Người dùng không phải là thành viên của nhóm này");
  }

  // Cannot change own role
  if (String(userId) === String(adminId)) {
    throw new Error("Bạn không thể thay đổi vai trò của chính mình");
  }

  // Update role
  participant.roles = newRole;
  await participant.save();

  return { success: true, conversationId, userId, newRole };
};

// Remove member from group (owner or admin)
exports.removeMember = async (conversationId, userId, adminId) => {
  const { conversation, isOwner } = await assertGroupManager(conversationId, adminId);

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Người dùng không phải là thành viên của nhóm này");
  }

  // Admin (deputy) cannot remove other admins
  if (!isOwner && participant.roles === "admin") {
    throw new Error("Phó nhóm không thể xóa phó nhóm khác");
  }

  // Cannot remove owner
  if (String(userId) === String(conversation.created_by)) {
    throw new Error("Không thể xóa trưởng nhóm");
  }

  if (String(userId) === String(adminId)) {
    throw new Error("Bạn không thể tự xóa chính mình. Hãy dùng chức năng rời nhóm");
  }

  // Hard delete: xóa hoàn toàn participant khỏi DB
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

// Transfer ownership of a group (owner only)
exports.transferOwnership = async (conversationId, currentOwnerId, newOwnerId) => {
  const conversation = await Conversation.findById(conversationId);

  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể chuyển quyền trưởng nhóm trong nhóm chat");
  }

  if (String(conversation.created_by) !== String(currentOwnerId)) {
    throw new Error("Chỉ trưởng nhóm hiện tại mới có quyền chuyển quyền");
  }
  
  if (String(currentOwnerId) === String(newOwnerId)) {
    throw new Error("Không thể chuyển quyền cho chính mình");
  }

  const newOwner = await Participant.findOne({
    conversation_id: conversationId,
    user_id: newOwnerId,
  });

  if (!newOwner) {
    throw new Error("Người được chuyển quyền không phải là thành viên của nhóm");
  }

  // Update conversation creator
  conversation.created_by = newOwnerId;
  await conversation.save();

  // Find previous owner and update to user/admin. Default down to admin
  const prevOwner = await Participant.findOne({
    conversation_id: conversationId,
    user_id: currentOwnerId,
  });
  
  if (prevOwner) {
    prevOwner.roles = "user";
    await prevOwner.save();
  }

  // Ensure new owner has admin/owner roles level representation if needed
  // In your current model, owner is just created_by, but maybe they were a user, let's make sure they are at least admin.
  newOwner.roles = "admin";
  await newOwner.save();

  return { 
    success: true, 
    conversationId, 
    oldOwnerId: currentOwnerId,
    newOwnerId 
  };
};

exports.assertGroupManager = assertGroupManager;
