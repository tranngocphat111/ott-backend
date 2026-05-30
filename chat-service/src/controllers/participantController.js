const ParticipantService = require("../services/participantService");
const ConversationService = require("../services/conversationService");
const UserService = require("../services/userService");
const Message = require("../models/Message");
const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");
const User = require("../models/User");
const { getActiveCall } = require("../services/callStateService");
const {
  publishMessageDelivered,
  publishMessageSeen,
} = require("../events/chatEvents");

const envEnabled = (name, defaultValue = true) => {
  const raw = process.env[name];
  if (raw === undefined) return defaultValue;
  return String(raw).toLowerCase() !== "false";
};

const chatReceiptQueueEnabled = envEnabled("CHAT_RECEIPT_QUEUE_ENABLED", false);

const normalizeMessageId = (value) => {
  const normalized = String(value || "0").trim();
  return /^\d+$/.test(normalized) ? normalized : "0";
};

const maxMessageId = (left, right) => {
  const safeLeft = normalizeMessageId(left);
  const safeRight = normalizeMessageId(right);
  try {
    return BigInt(safeLeft) >= BigInt(safeRight) ? safeLeft : safeRight;
  } catch {
    return safeLeft >= safeRight ? safeLeft : safeRight;
  }
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

const toPlainObject = (document) =>
  document && typeof document.toObject === "function"
    ? document.toObject()
    : { ...(document || {}) };

const isObjectIdLike = (value) => /^[a-f\d]{24}$/i.test(String(value || ""));

exports.getConversationsByUserId = async (req, res) => {
  try {
    const { userId } = req.params;

    await ParticipantService.ensureSelfConversation(userId);

    const participants =
      await ParticipantService.getConversationsByUserId(userId);

    const conversationIds = participants
      .map((participant) => participant.conversation_id?._id)
      .filter(Boolean);

    const memberParticipants = conversationIds.length
      ? await Participant.find({
          conversation_id: { $in: conversationIds },
          "settings.removed_from_group_at": null,
        })
          .select(
            "conversation_id user_id roles joined_at last_delivered_message_id last_delivered_at last_read_message_id last_read_at added_by status nickname",
          )
          .lean()
      : [];

    const memberUserIds = [
      ...new Set(
        memberParticipants
          .map((participant) => String(participant.user_id || "").trim())
          .filter(Boolean),
      ),
    ];

    const objectIdUserIds = memberUserIds.filter(isObjectIdLike);
    const userQuery = memberUserIds.length
      ? [
          { user_id: { $in: memberUserIds } },
          ...(objectIdUserIds.length ? [{ _id: { $in: objectIdUserIds } }] : []),
        ]
      : [];
    const users = userQuery.length
      ? await User.find({ $or: userQuery })
          .select("user_id name avatar is_online last_active_at")
          .lean()
      : [];

    const userById = new Map();
    users.forEach((user) => {
      if (user.user_id) userById.set(String(user.user_id), user);
      if (user._id) userById.set(String(user._id), user);
    });

    const membersByConversationId = new Map();
    memberParticipants.forEach((participant) => {
      const conversationId = String(participant.conversation_id || "");
      if (!conversationId) return;

      const user = userById.get(String(participant.user_id || "")) || null;
      const member = {
        _id: participant._id,
        user_id: participant.user_id,
        roles: participant.roles,
        joined_at: participant.joined_at,
        last_delivered_message_id:
          participant.last_delivered_message_id || "0",
        last_delivered_at: participant.last_delivered_at || null,
        last_read_message_id: participant.last_read_message_id || "0",
        last_read_at: participant.last_read_at || null,
        added_by: participant.added_by,
        status: participant.status,
        nickname: participant.nickname,
        user: user
          ? {
              name: user.name,
              avatar: user.avatar,
              is_online: user.is_online,
              last_active_at: user.last_active_at,
            }
          : null,
      };

      if (!membersByConversationId.has(conversationId)) {
        membersByConversationId.set(conversationId, []);
      }
      membersByConversationId.get(conversationId).push(member);
    });

    const unreadClauses = participants
      .map((participant) => {
        const conversation = participant.conversation_id;
        if (!conversation?._id) return null;

        const lastMsgId = conversation.last_message?.msg_id;
        const lastReadMsgId = normalizeMessageId(
          participant.last_read_message_id,
        );
        const deletedMsgId = normalizeMessageId(participant.deleted_msg_id);
        const anchorMsgId = maxMessageId(lastReadMsgId, deletedMsgId);

        if (!lastMsgId || !isMessageIdAfter(lastMsgId, anchorMsgId)) {
          return null;
        }

        return {
          conversation_id: conversation._id,
          msg_id: { $gt: anchorMsgId },
        };
      })
      .filter(Boolean);

    const unreadCounts = unreadClauses.length
      ? await Message.aggregate([
          {
            $match: {
              $or: unreadClauses,
              is_deleted: { $ne: true },
              is_revoked: { $ne: true },
              sender_id: { $ne: userId },
              deleted_for: { $ne: userId },
            },
          },
          { $group: { _id: "$conversation_id", count: { $sum: 1 } } },
        ])
      : [];

    const unreadCountByConversationId = new Map(
      unreadCounts.map((item) => [String(item._id), item.count || 0]),
    );

    // Trả về đầy đủ participant data + conversation data + unread_count
    const result = (
      await Promise.all(
        participants.map(async (participant) => {
          const conversation = participant.conversation_id;

          // Nếu conversation không tồn tại, bỏ qua
          if (!conversation) return null;

          const conversationId = String(conversation._id);
          const unread_count =
            unreadCountByConversationId.get(conversationId) || 0;
          const memberDetails =
            membersByConversationId.get(conversationId) || [];

          const senderNameById = new Map(
            memberDetails.map((member) => [
              String(member.user_id),
              member.nickname || member.user?.name || "",
            ]),
          );

          const resolvedLastMessage = conversation.last_message?.msg_id
            ? {
                msg_id: String(conversation.last_message.msg_id || ""),
                sender_id: String(conversation.last_message.sender_id || ""),
                sender_name:
                  senderNameById.get(
                    String(conversation.last_message.sender_id || ""),
                  ) ||
                  conversation.last_message.sender_name ||
                  "",
                content: buildConversationPreviewContent(
                  conversation.last_message,
                ),
                type: conversation.last_message.type,
                createdAt:
                  conversation.last_message.createdAt ||
                  conversation.updatedAt ||
                  new Date().toISOString(),
              }
            : undefined;

          const conversationData = toPlainObject(conversation);
          
          // Thêm thông tin cuộc gọi đang diễn ra (nếu có)
          const activeCall = getActiveCall(String(conversation._id));
          if (activeCall && activeCall.isGroup) {
            conversationData.is_calling = true;
            conversationData.call_participant_count = activeCall.participants.size;
          }

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
            membership_status: member.status || "joined",
            role: member.roles === "admin" ? "admin" : "member",
            joined_at: member.joined_at,
            last_delivered_message_id: member.last_delivered_message_id || "0",
            last_delivered_at: member.last_delivered_at || null,
            last_read_message_id: member.last_read_message_id || "0",
            last_read_at: member.last_read_at || null,
          }));

          return {
            conversation: conversationData,
            participant: {
              _id: participant._id,
              user_id: participant.user_id,
              conversation_id: participant.conversation_id._id,
              settings: participant.settings,
              last_delivered_message_id:
                participant.last_delivered_message_id || "0",
              last_delivered_at: participant.last_delivered_at || null,
              last_read_message_id: participant.last_read_message_id,
              last_read_at: participant.last_read_at,
              deleted_msg_id: participant.deleted_msg_id,
              unread_count,
              nickname: participant.nickname,
              joined_at: participant.joined_at,
              roles: participant.roles,
              status: participant.status || "joined",
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

    req.io.to(`user:${userId}`).emit("cap_nhat_phan_loai", {
      conversationId,
      userId,
      categoryId: categoryId ?? null,
      participant,
    });

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

    if (!participant) {
      return res.status(404).json({ error: "Participant not found" });
    }

    req.io.to(`user:${userId}`).emit("cap_nhat_thong_bao", {
      conversationId,
      userId,
      status: participant.settings?.notification_status,
      muteUntil: participant.settings?.mute_until || null,
      participant,
    });

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

    if (!participant) {
      return res.status(404).json({ error: "Participant not found" });
    }

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
      receiptType: "seen",
      participant: participantPayload,
    };

    const cursorChanged = participant.$locals?.cursorChanged !== false;

    if (cursorChanged) {
      req.io.to(`user:${userId}`).emit("conversation_read_synced", syncPayload);

      const joinedParticipants =
        await ParticipantService.getJoinedParticipants(conversationId);
      joinedParticipants.forEach((item) => {
        req.io.to(`user:${item.user_id}`).emit("participant_cursor_changed", {
          ...syncPayload,
          userId,
        });
      });

      if (chatReceiptQueueEnabled) {
        try {
          await publishMessageSeen({
            conversationId,
            userId,
            msgId,
            deviceId: req.body.deviceId || null,
          });
        } catch (publishError) {
          console.error(
            "[ParticipantController] Failed to publish seen receipt:",
            publishError.message,
          );
        }
      }
    }

    res.status(200).json(participantPayload);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateLastDelivered = async (req, res) => {
  try {
    const { conversationId, userId, msgId, deviceId } = req.body;
    const participant = await ParticipantService.updateLastDelivered(
      conversationId,
      userId,
      msgId,
    );

    if (!participant) {
      return res.status(404).json({ error: "Participant not found" });
    }

    if (participant.$locals?.cursorChanged !== false && chatReceiptQueueEnabled) {
      try {
        await publishMessageDelivered({
          conversationId,
          userId,
          msgId,
          deviceId: deviceId || null,
        });
      } catch (publishError) {
        console.error(
          "[ParticipantController] Failed to publish delivered receipt:",
          publishError.message,
        );
      }
    }

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
    
    // Get user details before leaving
    const leavingUser = await UserService.getUser(userId);
    const leavingName = leavingUser?.name || userId;
    
    const result = await ParticipantService.leaveGroup(conversationId, userId);

    // Emit to remaining members
    const participants =
      await ParticipantService.getParticipants(conversationId);
      
    // Create system message
    const systemMessage = await Message.create({
      conversation_id: conversationId,
      sender_id: userId,
      type: "system_leave",
      content: [`${leavingName} đã rời khỏi nhóm`],
      system_meta: {
        action: "member_leave",
        user_id: userId,
      },
    });

    await ConversationService.updateLastMessage(
      conversationId,
      systemMessage,
    );

    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", systemMessage);
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

    // Get user details for system message
    const [targetUser, adminUser] = await Promise.all([
      UserService.getUser(userId),
      UserService.getUser(adminId),
    ]);
    const targetName = targetUser?.name || userId;
    const adminName = adminUser?.name || adminId;
    const roleName = newRole === "admin" ? "phó nhóm" : "thành viên";
    const systemContent = `${targetName} đã được ${adminName} đặt làm ${roleName}`;

    // Create system message
    const systemMessage = await Message.create({
      conversation_id: conversationId,
      sender_id: adminId,
      type: "system_role_change",
      content: [systemContent],
      system_meta: {
        action: "role_updated",
        target_user_id: userId,
        new_role: newRole,
        updated_by: adminId,
      },
    });

    await ConversationService.updateLastMessage(
      conversationId,
      systemMessage,
    );

    // Emit to all participants
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", systemMessage);
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

    const [removedUser, adminUser] = await Promise.all([
      UserService.getUser(userId),
      UserService.getUser(adminId),
    ]);
    const removedName = removedUser?.name || userId;
    const adminName = adminUser?.name || adminId;
    const systemContent = `${removedName} đã bị đuổi khỏi nhóm bởi ${adminName}`;

    const participants =
      await ParticipantService.getParticipants(conversationId);

    // System notice for remaining members only
    const systemMessage = await Message.create({
      conversation_id: conversationId,
      sender_id: adminId,
      type: "system_leave",
      content: [systemContent],
      system_meta: {
        action: "member_removed",
        removed_user_id: userId,
        removed_by: adminId,
      },
    });

    await ConversationService.updateLastMessage(
      conversationId,
      systemMessage,
    );

    // Notify remaining members
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", systemMessage);
      req.io.to(`user:${p.user_id}`).emit("xoa_thanh_vien", result);
    });

    // Notify kicked user and disconnect from socket room
    req.io.to(`user:${userId}`).emit("bi_xoa_khoi_nhom", result);
    req.io.in(`user:${userId}`).socketsLeave(conversationId);

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

exports.transferOwnership = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { currentOwnerId, newOwnerId } = req.body;

    const result = await ParticipantService.transferOwnership(
      conversationId,
      currentOwnerId,
      newOwnerId,
    );

    // Get user details for system message
    const [oldOwner, newOwner] = await Promise.all([
      UserService.getUser(currentOwnerId),
      UserService.getUser(newOwnerId),
    ]);
    const oldName = oldOwner?.name || currentOwnerId;
    const newName = newOwner?.name || newOwnerId;
    const systemContent = `${oldName} đã nhường chức trưởng nhóm cho ${newName}`;

    // Create system message
    const systemMessage = await Message.create({
      conversation_id: conversationId,
      sender_id: currentOwnerId,
      type: "system_transfer_owner",
      content: [systemContent],
      system_meta: {
        action: "owner_transferred",
        old_owner_id: currentOwnerId,
        new_owner_id: newOwnerId,
      },
    });

    await ConversationService.updateLastMessage(
      conversationId,
      systemMessage,
    );

    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", systemMessage);
      req.io.to(`user:${p.user_id}`).emit("chuyen_quyen_truong_nhom", result);
    });

    res.status(200).json(result);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("Chỉ có thể") ||
      error.message.includes("Chỉ trưởng nhóm") ||
      error.message.includes("Không thể chuyển quyền") ||
      error.message.includes("không phải là thành viên");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

exports.acceptInvitation = async (req, res) => {
  try {
    const { conversationId, userId } = req.body;
    const participant = await Participant.findOneAndUpdate(
      { conversation_id: conversationId, user_id: userId },
      { status: "joined", joined_at: new Date() },
      { new: true }
    );

    if (!participant) {
      return res.status(400).json({ error: "Không tìm thấy lời mời" });
    }

    // Update member count
    await Conversation.findByIdAndUpdate(conversationId, {
      $inc: { member_count: 1 },
    });

    // Notify other members
    req.io.to(conversationId).emit("them_nguoi_moi", participant);
    
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.rejectInvitation = async (req, res) => {
  try {
    const { conversationId, userId } = req.body;
    const participant = await Participant.findOneAndDelete({
      conversation_id: conversationId,
      user_id: userId,
      status: "invited",
    });

    if (!participant) {
      return res.status(400).json({ error: "Không tìm thấy lời mời" });
    }

    res.status(200).json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
