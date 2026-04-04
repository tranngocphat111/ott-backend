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
    const { conversationId, senderId, content, type, size, replyToMsgId } =
      req.body;

    const savedMessage = await MessageService.sendMessage({
      conversationId,
      senderId,
      content,
      type,
      size,
      replyToMsgId,
    });

    // Emit đến user room riêng của từng participant thay vì conversation room
    // → nhận được ngay cả khi chưa join conversation room, xử lý được conversation mới
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan", savedMessage);
    });

    res.status(201).json(savedMessage);
  } catch (error) {
    if (error.message === "Tin nhắn trả lời không hợp lệ") {
      return res.status(400).json({ error: error.message });
    }
    res.status(500).json({ error: error.message });
  }
};

exports.getMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId } = req.query;

    let deletedMsgId = "0";
    if (userId) {
      const participant = await ParticipantService.getParticipant(
        conversationId,
        userId,
      );
      if (participant) {
        deletedMsgId = participant.deleted_msg_id || "0";
      }
    }

    const messages = await MessageService.getMessageHistory(
      conversationId,
      deletedMsgId,
    );

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.reactToMessage = async (req, res) => {
  try {
    const { msgId } = req.params;
    const { conversationId, userId, reactionType } = req.body;

    const updatedReaction = await MessageService.reactToMessage({
      conversationId,
      msgId,
      userId,
      reactionType,
    });

    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan_reaction", updatedReaction);
    });

    res.status(200).json(updatedReaction);
  } catch (error) {
    if (
      error.message === "Tin nhắn không tồn tại" ||
      error.message === "Reaction không hợp lệ"
    ) {
      return res.status(400).json({ error: error.message });
    }

    res.status(500).json({ error: error.message });
  }
};

// Pin/Unpin message
exports.pinMessage = async (req, res) => {
  try {
    const { msgId } = req.params;
    const { conversationId, userId, isPinned } = req.body;

    const updatedMessage = await MessageService.pinMessage({
      conversationId,
      msgId,
      userId,
      isPinned,
    });

    // Emit to all participants
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan_pin", updatedMessage);
    });

    res.status(200).json(updatedMessage);
  } catch (error) {
    if (error.message === "Tin nhắn không tồn tại") {
      return res.status(400).json({ error: error.message });
    }
    res.status(500).json({ error: error.message });
  }
};

// Get pinned messages
exports.getPinnedMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;

    const messages = await MessageService.getPinnedMessages(conversationId);

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Get media messages (images/videos)
exports.getMediaMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { limit = 20, skip = 0 } = req.query;

    const messages = await MessageService.getMediaMessages(
      conversationId,
      parseInt(limit),
      parseInt(skip)
    );

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Get file messages
exports.getFileMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { limit = 20, skip = 0 } = req.query;

    const messages = await MessageService.getFileMessages(
      conversationId,
      parseInt(limit),
      parseInt(skip)
    );

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Get link messages
exports.getLinkMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { limit = 20, skip = 0 } = req.query;

    const messages = await MessageService.getLinkMessages(
      conversationId,
      parseInt(limit),
      parseInt(skip)
    );

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Global search for contacts/conversations/messages/files/media
exports.searchEverything = async (req, res) => {
  try {
    const { userId } = req.params;
    const { q = "", limit = 20, senderId } = req.query;

    const results = await MessageService.searchEverything({
      userId,
      keyword: q,
      limit: parseInt(limit, 10),
      senderId,
    });

    res.status(200).json(results);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
