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

    req.io.to(conversationId).emit("tin_nhan", savedMessage);
    console.log(
      `${senderId} gui tin nhan : ${content} vao cuoc trof chuyen ${conversationId}`,
    );

    res.status(201).json(savedMessage);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.getMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;

    const messages = await MessageService.getMessageHistory(conversationId);

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
