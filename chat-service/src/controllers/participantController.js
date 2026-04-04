const ParticipantService = require("../services/participantService");
const Message = require("../models/Message");

const buildConversationPreviewContent = (message) => {
  if (!message) return "";

  const rawContent = Array.isArray(message.content)
    ? String(message.content[0] || "")
    : String(message.content || "");

  switch (message.type) {
    case "image":
      return "[Hình ảnh]";
    case "video":
      return "[Video]";
    case "audio":
      return "[Âm thanh]";
    case "file":
      return "[Tệp tin]";
    default:
      return rawContent.length > 50
        ? `${rawContent.substring(0, 50)}...`
        : rawContent;
  }
};

exports.getConversationsByUserId = async (req, res) => {
  try {
    const { userId } = req.params;

    const participants =
      await ParticipantService.getConversationsByUserId(userId);
    // Trả về đầy đủ participant data + conversation data + unread_count
    const result = (
      await Promise.all(
        participants.map(async (participant) => {
          const conversation = participant.conversation_id;

          // Nếu conversation không tồn tại, bỏ qua
          if (!conversation) return null;

          const lastReadMsgId = participant.last_read_message_id || "0";
          const deletedMsgId = participant.deleted_msg_id || "0";
          const anchorMsgId =
            BigInt(lastReadMsgId) > BigInt(deletedMsgId)
              ? lastReadMsgId
              : deletedMsgId;

          // Last message hiển thị ở sidebar phải theo phạm vi nhìn thấy của chính user.
          const visibleLastMessage = await Message.findOne({
            conversation_id: conversation._id,
            is_deleted: { $ne: true },
            deleted_for: { $ne: userId },
          })
            .sort({ msg_id: -1 })
            .select("msg_id sender_id type content createdAt")
            .lean();

          let unread_count = 0;
          try {
            if (
              visibleLastMessage?.msg_id &&
              BigInt(visibleLastMessage.msg_id) > BigInt(anchorMsgId)
            ) {
              // For numeric comparison with large numbers in MongoDB, we need to use $where or numeric operators
              // Since msg_id is a string, we need to compare them as BigInt in JavaScript
              const messages = await Message.find({
                conversation_id: conversation._id,
                is_deleted: { $ne: true },
                is_revoked: { $ne: true },
                deleted_for: { $ne: userId },
              })
                .select("msg_id")
                .lean();

              unread_count = messages.filter((m) => {
                try {
                  return BigInt(m.msg_id) > BigInt(anchorMsgId);
                } catch {
                  return false;
                }
              }).length;
            }
          } catch (error) {
            console.error("Error calculating unread count:", error);
            unread_count = 0;
          }

          const memberDetails = await ParticipantService.getConversationMembers(
            conversation._id,
          );

          const senderNameById = new Map(
            memberDetails.map((member) => [
              String(member.user_id),
              member.nickname || member.user?.name || "",
            ]),
          );

          const resolvedLastMessage = visibleLastMessage
            ? {
                msg_id: String(visibleLastMessage.msg_id || ""),
                sender_id: String(visibleLastMessage.sender_id || ""),
                sender_name:
                  senderNameById.get(
                    String(visibleLastMessage.sender_id || ""),
                  ) || "",
                content: buildConversationPreviewContent(visibleLastMessage),
                type:
                  visibleLastMessage.type === "system_add"
                    ? "text"
                    : visibleLastMessage.type,
                createdAt:
                  visibleLastMessage.createdAt || new Date().toISOString(),
              }
            : undefined;

          const conversationData = conversation.toObject();
          conversationData.last_message = resolvedLastMessage;
          conversationData.participants = memberDetails.map((member) => ({
            _id: member.user_id,
            user_id: member.user_id,
            display_name:
              member.nickname || member.user?.name || member.user_id,
            nickname: member.nickname || "",
            name: member.user?.name || "",
            avatar: member.user?.avatar || "",
            status: member.user?.is_online ? "online" : "offline",
            role: member.roles === "admin" ? "admin" : "member",
            joined_at: member.joined_at,
          }));

          return {
            conversation: conversationData,
            participant: {
              _id: participant._id,
              user_id: participant.user_id,
              conversation_id: participant.conversation_id._id,
              settings: participant.settings,
              last_read_message_id: participant.last_read_message_id,
              last_read_at: participant.last_read_at,
              deleted_msg_id: participant.deleted_msg_id,
              unread_count,
              nickname: participant.nickname,
              joined_at: participant.joined_at,
              roles: participant.roles,
            },
          };
        }),
      )
    ).filter((item) => item !== null);

    res.status(200).json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateConversationCategory = async (req, res) => {
  try {
    const { conversationId, userId, categoryId } = req.body;
    const participant = await ParticipantService.updateConversationCategory(
      conversationId,
      userId,
      categoryId,
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateNotificationStatus = async (req, res) => {
  try {
    const { conversationId, userId, status, muteUntil } = req.body;
    const participant = await ParticipantService.updateNotificationStatus(
      conversationId,
      userId,
      status,
      muteUntil,
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updatePinStatus = async (req, res) => {
  try {
    const { conversationId, userId, isPinned } = req.body;
    const participant = await ParticipantService.updatePinStatus(
      conversationId,
      userId,
      isPinned,
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateLastRead = async (req, res) => {
  try {
    const { conversationId, userId, msgId } = req.body;
    const participant = await ParticipantService.updateLastRead(
      conversationId,
      userId,
      msgId,
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.deleteConversation = async (req, res) => {
  try {
    const { conversationId, userId } = req.body;
    const participant = await ParticipantService.deleteConversation(
      conversationId,
      userId,
    );
    res.status(200).json(participant);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("chưa có tin nhắn");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

// Get conversation members with user details
exports.getConversationMembers = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const members =
      await ParticipantService.getConversationMembers(conversationId);
    res.status(200).json(members);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Leave group
exports.leaveGroup = async (req, res) => {
  try {
    const { conversationId, userId } = req.params;
    const result = await ParticipantService.leaveGroup(conversationId, userId);

    // Emit to remaining members
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("roi_nhom", result);
    });

    res.status(200).json(result);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("không phải") ||
      error.message.includes("Chỉ có thể");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

// Update member role (admin only)
exports.updateMemberRole = async (req, res) => {
  try {
    const { conversationId, userId } = req.params;
    const { adminId, newRole } = req.body;

    const result = await ParticipantService.updateMemberRole(
      conversationId,
      userId,
      newRole,
      adminId,
    );

    // Emit to all participants
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("cap_nhat_role", result);
    });

    res.status(200).json(result);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("không có quyền") ||
      error.message.includes("trưởng nhóm") ||
      error.message.includes("không phải") ||
      error.message.includes("Chỉ có thể") ||
      error.message.includes("Không thể");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

// Remove member from group (admin only)
exports.removeMember = async (req, res) => {
  try {
    const { conversationId, userId } = req.params;
    const { adminId } = req.body;

    const result = await ParticipantService.removeMember(
      conversationId,
      userId,
      adminId,
    );

    // Emit to all participants including removed user
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("xoa_thanh_vien", result);
    });
    // Also notify the removed user
    req.io.to(`user:${userId}`).emit("bi_xoa_khoi_nhom", result);

    res.status(200).json(result);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("không có quyền") ||
      error.message.includes("trưởng nhóm") ||
      error.message.includes("không phải") ||
      error.message.includes("Chỉ có thể") ||
      error.message.includes("Không thể xóa");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

exports.updateMemberNickname = async (req, res) => {
  try {
    const { conversationId, userId } = req.params;
    const { requesterId, nickname } = req.body;

    const result = await ParticipantService.updateMemberNickname(
      conversationId,
      userId,
      requesterId,
      nickname,
    );

    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("cap_nhat_biet_danh", result);
    });

    res.status(200).json(result);
  } catch (error) {
    const isClientError =
      error.message.includes("không thuộc") ||
      error.message.includes("không tồn tại");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};
