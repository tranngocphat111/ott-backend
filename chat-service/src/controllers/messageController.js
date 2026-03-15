const MessageService = require("../services/messageService");
const ParticipantService = require("../services/participantService");

exports.generatePresignedUrl = async (req, res) => {
  try {
    const { fileName, fileType } = req.body;

    const data = await MessageService.generatePresignedUrl(fileName, fileType);

    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.sendMessage = async (req, res) => {
  try {
    const { conversationId, senderId, content, type, size } = req.body;

    const savedMessage = await MessageService.sendMessage({
      conversationId,
      senderId,
      content,
      type,
      size,
    });

    // Emit đến user room riêng của từng participant thay vì conversation room
    // → nhận được ngay cả khi chưa join conversation room, xử lý được conversation mới
    const participants = await ParticipantService.getParticipants(conversationId);
    participants.forEach(p => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", savedMessage);
    });

    res.status(201).json(savedMessage);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.getMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId } = req.query;

    let deletedMsgId = "0";
    if (userId) {
      const participant = await ParticipantService.getParticipant(conversationId, userId);
      if (participant) {
        deletedMsgId = participant.deleted_msg_id || "0";
      }
    }

    const messages = await MessageService.getMessageHistory(conversationId, deletedMsgId);

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
