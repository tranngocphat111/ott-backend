const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");
const MessageService = require("../services/messageService");
const UserService = require("../services/userService");
const Conversation = require("../models/Conversation");
const Message = require("../models/Message");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type, memberIds, memberNames, name, avatar } = req.body;
    if (type === "private" && memberIds && memberIds.length > 0) {
      const targetUserId = memberIds[0];
      const conversation = await ConversationService.findOrCreatePrivateConversation(creatorId, targetUserId);
      const updatedConversation = await ConversationService.getConversationById(conversation._id);
      
      if (!updatedConversation) {
        throw new Error("Không thể khởi tạo cuộc hội thoại");
      }

      // Emit to both users
      [creatorId, targetUserId].forEach(userId => {
        req.io.to(`user:${userId}`).emit("tao_phong_moi", updatedConversation);
      });
      
      return res.status(200).json(updatedConversation);
    }

    // Creating a group or other type
    let role = "user";
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
      name: name || "",
      avatar: avatar || "",
      memberCount: memberIds ? [...new Set(memberIds.filter(id => id !== creatorId))].length + 1 : 1,
    });

    if (type === "group") {
      role = "admin";
    }

    // Thêm creator vào nhóm
    await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: creatorId,
      role: role,
    });

    // Thêm các member khác vào nhóm
    const otherMemberIds = memberIds ? [...new Set(memberIds.filter(id => id !== creatorId))] : [];
    if (otherMemberIds.length > 0) {
      const addedParticipants = await Promise.all(
        otherMemberIds.map(async (userId) => {
          // Check relationship status
          const RelationshipService = require("../services/relationshipService");
          const relationship = await RelationshipService.getRelationshipBetween(creatorId, userId);
          const isFriend = relationship && relationship.status === "ACCEPTED";
          
          const member = await ParticipantService.addParticipant({
            conversationId: conversation._id,
            userId: userId,
            role: "user",
            status: isFriend ? "joined" : "invited",
          });

          req.io.to(conversation._id.toString()).emit("them_nguoi_moi", member);

          console.log(
            `${userId} da duoc them vao phong ${conversation._id} o database`,
          );

          return member;
        }),
      );

      // Filter only joined members for the notification message
      const joinedParticipants = addedParticipants.filter(p => p.status === 'joined');
      
      if (joinedParticipants.length > 0) {
        // Lấy thông tin các thành viên được thêm vào để tạo tin nhắn thông báo
        const memberDisplayNames = await Promise.all(
          joinedParticipants.map(async (p) => {
            const user = await UserService.getUser(p.user_id);
            return user ? user.name : "Người dùng";
          })
        );

        // Tạo nội dung thông báo: "Hoài Nhân, Giang Trần, Phạm Thịnh được bạn thêm vào nhóm"
        const creatorUser = await UserService.getUser(creatorId);
        const creatorName = creatorUser ? creatorUser.name : "Trưởng nhóm";
        const memberNamesJoined = memberDisplayNames.join(", ");
        
        // Gửi thông báo cho nhóm
        const notificationMessage = await MessageService.sendMessage({
          conversationId: conversation._id,
          senderId: creatorId,
          content: `${creatorName} đã thêm ${memberNamesJoined} vào nhóm`,
          type: "system_add",
        });

        console.log("Tin nhắn thông báo đã được tạo:", notificationMessage);
      }
    }

    // Lấy lại conversation đã được cập nhật với last_message
    const updatedConversation = await ConversationService.getConversationById(conversation._id);

    // Emit chỉ tới user room của từng thành viên (không broadcast toàn bộ)
    const allParticipantIds = [creatorId, ...(memberIds || [])];
    allParticipantIds.forEach(userId => {
      req.io.to(`user:${userId}`).emit("tao_phong_moi", updatedConversation);
    });
    console.log(`Phong ${conversation._id} moi duoc tao ra o database`);

    res.status(201).json(updatedConversation);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.addMember = async (req, res) => {
  try {
    const { conversationId, userId, userIds, addedBy } = req.body;
    
    const memberIds = userIds || (userId ? [userId] : []);
    
    if (memberIds.length === 0) {
      return res.status(400).json({ error: "Cần ít nhất một thành viên" });
    }

    const conversation = await Conversation.findById(conversationId);
    if (!conversation) {
      return res.status(400).json({ error: "Cuộc hội thoại không tồn tại" });
    }

    if (conversation.type === "group") {
      const isMember = await ParticipantService.getParticipant(conversationId, addedBy);
      if (!isMember) {
        return res.status(403).json({ error: "Bạn không phải thành viên của nhóm" });
      }
    }

    const lastMsgId = "0";
    const addedMembers = [];

    for (const memberId of memberIds) {
      const RelationshipService = require("../services/relationshipService");
      const relationship = await RelationshipService.getRelationshipBetween(addedBy, memberId);
      const isFriend = relationship && relationship.status === "ACCEPTED";

      const member = await ParticipantService.addParticipant({
        conversationId,
        userId: memberId,
        role: "user",
        addedBy: addedBy,
        lastMsgId,
        status: isFriend ? "joined" : "invited",
      });
      addedMembers.push(member);
    }

    const joinedMembers = addedMembers.filter(m => m.status === 'joined');
    const joinedCount = joinedMembers.length;
    let responseMessage = null;

    if (joinedCount > 0) {
      await Conversation.findByIdAndUpdate(conversationId, {
        $inc: { member_count: joinedCount },
      });

      const adder = await UserService.getUser(addedBy);
      const adderName = adder ? adder.name : "Thành viên";
      
      const memberDisplayNames = await Promise.all(
        joinedMembers.map(async (m) => {
          const user = await UserService.getUser(m.user_id);
          return user ? user.name : "Người dùng";
        })
      );

      const memberNamesStr = memberDisplayNames.join(", ");

      const notificationMessage = await MessageService.sendMessage({
        conversationId: conversation._id,
        senderId: addedBy,
        content: `${adderName} đã thêm ${memberNamesStr} vào nhóm`,
        type: "system_add",
      });

      await Message.findByIdAndUpdate(notificationMessage._id, {
        $set: {
          system_meta: {
            action: "member_added",
            added_by: addedBy,
            added_user_ids: joinedMembers.map(m => m.user_id),
          },
        },
      });

      responseMessage = await Message.findById(notificationMessage._id).lean();

      const joinedParticipants = await ParticipantService.getJoinedParticipants(conversationId);
      joinedParticipants.forEach((p) => {
        addedMembers.forEach((member) => {
          req.io.to(`user:${p.user_id}`).emit("them_nguoi_moi", member);
        });
        req.io.to(`user:${p.user_id}`).emit("tin_nhan", responseMessage);
      });
    } else {
      const joinedParticipants = await ParticipantService.getJoinedParticipants(conversationId);
      joinedParticipants.forEach((p) => {
        addedMembers.forEach((member) => {
          req.io.to(`user:${p.user_id}`).emit("them_nguoi_moi", member);
        });
      });
    }

    const updatedConversation = await ConversationService.getConversationById(conversationId);
    memberIds.forEach((memberId) => {
      req.io.to(`user:${memberId}`).emit("tao_phong_moi", updatedConversation);
    });
    
    console.log(`${memberIds.length} members processed for room ${conversationId}`);

    res.status(200).json({ members: addedMembers, message: responseMessage });
  } catch (error) {
    console.error("Add member error:", error);
    res.status(500).json({ error: error.message });
  }
};

exports.dissolveGroup = async (req, res) => {
  try {
    const { conversationId, userId } = req.params;
    const requester = await UserService.getUser(userId);
    const requesterName = requester?.name || userId;
    const result = await ConversationService.dissolveGroup(conversationId, userId);

    result.affectedUserIds.forEach((targetUserId) => {
      req.io.to(`user:${targetUserId}`).emit("tin_nhan", result.finalNotice);
      req.io.to(`user:${targetUserId}`).emit("giai_tan_nhom", {
        conversationId,
        dissolvedBy: userId,
        dissolvedByName: requesterName,
        message: String(result.finalNotice?.content?.[0] || "Nhóm đã được giải tán"),
        deleteForOwner: String(targetUserId) === String(result.ownerId),
      });
      req.io.in(`user:${targetUserId}`).socketsLeave(conversationId);
    });

    res.status(200).json({
      success: true,
      conversationId,
      deletedMessages: result.deletedMessages,
      deletedParticipants: result.deletedParticipants,
    });
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("không có quyền") ||
      error.message.includes("Chỉ trưởng nhóm");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

exports.updateConversation = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const updateData = req.body;

    const conversation = await ConversationService.updateConversation(
      conversationId,
      updateData
    );

    // Emit to all participants
    const participants = await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("cap_nhat_nhom", conversation);
    });

    res.status(200).json(conversation);
  } catch (error) {
    const isClientError =
      error.message.includes("không tồn tại") ||
      error.message.includes("không có quyền") ||
      error.message.includes("Chỉ có thể");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};

// ─── Invite Link ─────────────────────────────────────────────────────────────

/**
 * POST /conversations/:conversationId/invite-link
 */
exports.getInviteLink = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { requesterId } = req.body;
    if (!requesterId) return res.status(400).json({ error: "requesterId la bat buoc" });
    const baseUrl = process.env.FRONTEND_URL || `${req.protocol}://${req.get("host")}`;
    const result = await ConversationService.getOrCreateInviteLink(conversationId, requesterId, baseUrl);
    res.status(200).json(result);
  } catch (error) {
    const isClient = error.message.includes("khong ton tai") || error.message.includes("giai tan") || error.message.includes("thanh vien");
    res.status(isClient ? 400 : 500).json({ error: error.message });
  }
};

/**
 * POST /conversations/join-by-link
 */
exports.joinByLink = async (req, res) => {
  try {
    const { token, userId } = req.body;
    if (!token || !userId) return res.status(400).json({ error: "token va userId la bat buoc" });
    const result = await ConversationService.joinByInviteToken(token, userId);
    const conversation = result.conversation;
    const isNewJoin = result.isNewJoin;

    const participants = await ParticipantService.getParticipants(conversation._id.toString());
    
    let responseMessage = null;
    if (isNewJoin) {
      const user = await UserService.getUser(userId);
      const userName = user ? user.name : "Người dùng";
      
      const notificationMessage = await MessageService.sendMessage({
        conversationId: conversation._id,
        senderId: userId,
        content: `${userName} đã tham gia nhóm`,
        type: "system_add",
      });
      const Message = require("../models/Message");
      responseMessage = await Message.findById(notificationMessage._id).lean();
    }

    participants.forEach((p) => {
      if (String(p.user_id) !== String(userId)) {
        req.io.to(`user:${p.user_id}`).emit("them_nguoi_moi", { user_id: userId, conversation_id: conversation._id, status: "joined" });
      }
      if (responseMessage) {
        req.io.to(`user:${p.user_id}`).emit("tin_nhan", responseMessage);
      }
    });
    
    req.io.to(`user:${userId}`).emit("tao_phong_moi", conversation);
    res.status(200).json({ conversation, isNewJoin });
  } catch (error) {
    const isClient = error.message.includes("hop le") || error.message.includes("giai tan");
    res.status(isClient ? 400 : 500).json({ error: error.message });
  }
};

exports.blockMember = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId, adminId } = req.body;
    if (!userId || !adminId) return res.status(400).json({ error: "userId và adminId là bắt buộc" });
    
    const result = await ConversationService.blockMember(conversationId, userId, adminId);
    
    // Emit to target user that they are removed/blocked
    req.io.to(`user:${userId}`).emit("bi_chan_khoi_nhom", { conversationId });
    
    // Emit to other group members
    const participants = await ParticipantService.getJoinedParticipants(conversationId);
    participants.forEach(p => {
      req.io.to(`user:${p.user_id}`).emit("nguoi_dung_bi_chan", { conversationId, userId });
    });

    res.status(200).json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.unblockMember = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId, adminId } = req.body;
    if (!userId || !adminId) return res.status(400).json({ error: "userId và adminId là bắt buộc" });

    const result = await ConversationService.unblockMember(conversationId, userId, adminId);
    res.status(200).json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.getBlockedMembers = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { requesterId } = req.query;
    if (!requesterId) return res.status(400).json({ error: "requesterId là bắt buộc" });

    const result = await ConversationService.getBlockedGroupMembers(conversationId, requesterId);
    res.status(200).json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

/**
 * GET /conversations/invite-link/:token
 */
exports.getInviteLinkInfo = async (req, res) => {
  try {
    const { token } = req.params;
    const { userId } = req.query;
    if (!token) return res.status(400).json({ error: "Token la bat buoc" });
    const result = await ConversationService.getInfoByInviteToken(token, userId);
    res.status(200).json(result);
  } catch (error) {
    const isClient = error.message.includes("hop le") || error.message.includes("giai tan");
    res.status(isClient ? 400 : 500).json({ error: error.message });
  }
};