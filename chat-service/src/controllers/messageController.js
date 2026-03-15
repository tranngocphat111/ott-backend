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
      size
    });

    req.io.to(conversationId).emit("tin_nhan", savedMessage);
    console.log(
      `${senderId} gui tin nhan ${content} vao cuoc tro chuyen ${conversationId}`,
    );

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
