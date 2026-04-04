const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");
const MessageService = require("../services/messageService");
const UserService = require("../services/userService");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type, memberIds, name, avatar } = req.body;
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
          const member = await ParticipantService.addParticipant({
            conversationId: conversation._id,
            userId: userId,
            role: "user",
          });

          req.io.to(conversation._id.toString()).emit("them_nguoi_moi", member);

          console.log(
            `${userId} da duoc them vao phong ${conversation._id} o database`,
          );

          return member;
        }),
      );

      // Lấy thông tin các thành viên được thêm vào để tạo tin nhắn thông báo
      const memberUsers = await Promise.all(
        memberIds.map(async (userId) => {
          const user = await UserService.getUser(userId);
          return user ? user.name : "Unknown";
        })
      );

      // Tạo nội dung thông báo: "Hoài Nhân, Giang Trần, Phạm Thịnh được bạn thêm vào nhóm"
      const memberNames = memberUsers.join(", ");
      const notificationContent = `${memberNames} được bạn thêm vào nhóm`;

      // Tạo tin nhắn thông báo hệ thống
      const notificationMessage = await MessageService.sendMessage({
        conversationId: conversation._id,
        senderId: creatorId,
        content: notificationContent,
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

    const Conversation = require("../models/Conversation");
    const addedMembers = [];

    for (const memberId of memberIds) {
      const member = await ParticipantService.addParticipant({
        conversationId,
        userId: memberId,
        role: "user",
        addedBy: addedBy,
      });
      addedMembers.push(member);
    }

    // Update member count
    await Conversation.findByIdAndUpdate(conversationId, {
      $inc: { member_count: memberIds.length },
    });

    // Get user info for notification message
    const memberUsers = await Promise.all(
      memberIds.map(async (id) => {
        const user = await UserService.getUser(id);
        return user ? user.name : "Unknown";
      })
    );

    // Create system notification message
    const adderUser = await UserService.getUser(addedBy);
    const adderName = adderUser ? adderUser.name : "Ai đó";
    const memberNames = memberUsers.join(", ");
    const notificationContent = `${memberNames} được ${adderName} thêm vào nhóm`;

    const notificationMessage = await MessageService.sendMessage({
      conversationId,
      senderId: addedBy,
      content: notificationContent,
      type: "system_add",
    });

    // Emit to all existing participants
    const participants = await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      addedMembers.forEach((member) => {
        req.io.to(`user:${p.user_id}`).emit("them_nguoi_moi", member);
      });
    });

    // Emit to new members so they can get the conversation
    const conversation = await ConversationService.getConversationById(conversationId);
    memberIds.forEach((memberId) => {
      req.io.to(`user:${memberId}`).emit("tao_phong_moi", conversation);
    });
    
    console.log(
      `${memberIds.length} members added to room ${conversationId}`,
    );

    res.status(200).json({ members: addedMembers, message: notificationMessage });
  } catch (error) {
    res.status(500).json({ error: error.message });
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