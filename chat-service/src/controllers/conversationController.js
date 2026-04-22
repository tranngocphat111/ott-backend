const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");
const MessageService = require("../services/messageService");
const UserService = require("../services/userService");
const Conversation = require("../models/Conversation");
const Message = require("../models/Message");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type, memberIds, memberNames, name, avatar } = req.body;
    let role = "user";
    
    // Tạo conversation với đầy đủ field
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
      name: name || "",
      avatar: avatar || "",
      memberCount: memberIds ? memberIds.length + 1 : 1,
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

    // Thêm các member vào nhóm và tạo tin nhắn thông báo
    if (memberIds && Array.isArray(memberIds) && memberIds.length > 0) {
      await Promise.all(
        memberIds.map(async (userId) => {
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

      // Lấy thông tin các thành viên được thêm vào để tạo tin nhắn thông báo
      const memberDisplayNames = await Promise.all(
        memberIds.map(async (userId, index) => {
          const user = await UserService.getUser(userId);
          if (user) return user.name;
          // Fallback to name passed from frontend for strangers
          return (memberNames && memberNames[index]) || "Người dùng";
        })
      );

      // Tạo nội dung thông báo: "Hoài Nhân, Giang Trần, Phạm Thịnh được bạn thêm vào nhóm"
      // Tạo tin nhắn thông báo hệ thống
      const creatorUser = await UserService.getUser(creatorId);
      const creatorName = creatorUser ? creatorUser.name : "Trưởng nhóm";
      const memberNamesJoined = memberDisplayNames.join(", ");
      
      // Gửi thông báo cho nhóm: "A, B được C thêm vào nhóm"
      const notificationMessage = await MessageService.sendMessage({
        conversationId: conversation._id,
        senderId: creatorId,
        content: `${memberNamesJoined} được ${creatorName} thêm vào nhóm`,
        type: "system_add",
      });

      console.log("Tin nhắn thông báo đã được tạo:", notificationMessage);
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
    
    // Support both single userId and multiple userIds
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

    const lastMsgId = "0"; // Set to "0" to allow reading old messages

    const addedMembers = [];

    for (const memberId of memberIds) {
      // Check relationship status
      const RelationshipService = require("../services/relationshipService");
      const relationship = await RelationshipService.getRelationshipBetween(addedBy, memberId);
      const isFriend = relationship && relationship.status === "ACCEPTED";

      const member = await ParticipantService.addParticipant({
        conversationId,
        userId: memberId,
        role: "user",
        addedBy: addedBy,
        lastMsgId, // Pass lastMsgId to hide previous messages
        status: isFriend ? "joined" : "invited",
      });
      addedMembers.push(member);
    }

    // Update member count (only count joined members)
    const joinedCount = addedMembers.filter(m => m.status === 'joined').length;
    if (joinedCount > 0) {
      await Conversation.findByIdAndUpdate(conversationId, {
        $inc: { member_count: joinedCount },
      });
    }

    // Lấy thông tin người thêm và các thành viên được thêm
    const adder = await UserService.getUser(addedBy);
    const adderName = adder ? adder.name : "Thành viên";
    
    const memberDisplayNames = await Promise.all(
      memberIds.map(async (id) => {
        const user = await UserService.getUser(id);
        return user ? user.name : "Người dùng";
      })
    );

    const memberNamesStr = memberDisplayNames.join(", ");

    // Tạo tin nhắn thông báo hệ thống
    const notificationMessage = await MessageService.sendMessage({
      conversationId: conversation._id,
      senderId: addedBy,
      content: `${memberNamesStr} được ${adderName} thêm vào nhóm`,
      type: "system_add",
    });

    await Message.findByIdAndUpdate(notificationMessage._id, {
      $set: {
        system_meta: {
          action: "member_added",
          added_by: addedBy,
          added_user_ids: memberIds,
        },
      },
    });

    const finalNotificationMessage = await Message.findById(notificationMessage._id).lean();

    // Emit system message only to JOINED participants (invited users should NOT see messages)
    const joinedParticipants = await ParticipantService.getJoinedParticipants(conversationId);
    joinedParticipants.forEach((p) => {
      addedMembers.forEach((member) => {
        req.io.to(`user:${p.user_id}`).emit("them_nguoi_moi", member);
      });
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", finalNotificationMessage || notificationMessage);
    });

    // Emit to new members so they can get the conversation
    const updatedConversation = await ConversationService.getConversationById(conversationId);
    memberIds.forEach((memberId) => {
      req.io.to(`user:${memberId}`).emit("tao_phong_moi", updatedConversation);
    });
    
    console.log(
      `${memberIds.length} members added to room ${conversationId}`,
    );

    res.status(200).json({ members: addedMembers, message: finalNotificationMessage || notificationMessage });
  } catch (error) {
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

// Update conversation (name, avatar)
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
      error.message.includes("Chỉ có thể");
    res.status(isClientError ? 400 : 500).json({ error: error.message });
  }
};