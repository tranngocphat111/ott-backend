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

          req.io.to(conversation._id).emit("them_nguoi_moi", member);

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
        type: "text",
      });

      console.log("Tin nhắn thông báo đã được tạo:", notificationMessage);
    }

    // Lấy lại conversation đã được cập nhật với last_message
    const updatedConversation = await ConversationService.getConversationById(conversation._id);

    req.io.emit("tao_phong_moi", updatedConversation);
    console.log(`Phong ${conversation._id} moi duoc tao ra o database`);

    res.status(201).json(updatedConversation);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.addMember = async (req, res) => {
  try {
    const { conversationId, userId } = req.body;

    const member = await ParticipantService.addParticipant({
      conversationId,
      userId,
      role: "user",
    });

    req.io.to(conversationId).emit("them_nguoi_moi", member);
    console.log(
      `${userId} da duoc them vao phong ${conversationId} o database`,
    );

    res.status(200).json(member);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};


