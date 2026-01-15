const MessageService = require("../services/messageService");

exports.sendMessage = async (req, res) => {
  try {
    const { conversationId, senderId, content, type } = req.body;

    const savedMessage = await MessageService.sendMessage({
      conversationId,
      senderId,
      content,
      type,
    });

    req.io.to(conversationId).emit("receive_message", savedMessage);

    res.status(201).json(savedMessage);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
