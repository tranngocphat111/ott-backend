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

exports.forwardMessage = async (req, res) => {
  try {
    const { originalMsgId, conversationId, targetConversationIds, senderId } = req.body;

    if (!originalMsgId || !conversationId || !targetConversationIds || !targetConversationIds.length || !senderId) {
      return res.status(400).json({ error: "Thiếu thông tin bắt buộc để chuyển tiếp" });
    }

    const forwardedMessages = await MessageService.forwardMessage({
      originalMsgId,
      conversationId,
      targetConversationIds,
      senderId,
    });

    // Emit đến user room riêng của từng participant trong mỗi conversation được forward tới
    for (let i = 0; i < targetConversationIds.length; i++) {
      const targetConversationId = targetConversationIds[i];
      const savedMessage = forwardedMessages[i];
      
      if (savedMessage) {
        const participants = await ParticipantService.getParticipants(targetConversationId);
        participants.forEach((p) => {
          req.io.to(`user:${p.user_id}`).emit("tin_nhan", savedMessage);
        });
      }
    }

    res.status(200).json({ results: forwardedMessages });
  } catch (error) {
    if (
      error.message === "Tin nhắn gốc không tồn tại" ||
      error.message === "Không thể chuyển tiếp tin nhắn đã bị xóa hoặc thu hồi" ||
      error.message === "Loại tin nhắn này chưa hỗ trợ chuyển tiếp"
    ) {
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
      userId,
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

exports.revokeMessage = async (req, res) => {
  try {
    const { msgId } = req.params;
    const { conversationId, userId } = req.body;

    const revokedMessage = await MessageService.revokeMessage({
      conversationId,
      msgId,
      userId,
    });

    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io.to(`user:${p.user_id}`).emit("tin_nhan_thu_hoi", revokedMessage);
      if (revokedMessage.systemMessage) {
        req.io
          .to(`user:${p.user_id}`)
          .emit("tin_nhan", revokedMessage.systemMessage);
      }
    });

    res.status(200).json(revokedMessage);
  } catch (error) {
    if (
      error.message === "Tin nhắn không tồn tại" ||
      error.message === "Bạn không có quyền thu hồi tin nhắn này"
    ) {
      return res.status(400).json({ error: error.message });
    }

    res.status(500).json({ error: error.message });
  }
};

exports.deleteMessage = async (req, res) => {
  try {
    const { msgId } = req.params;
    const { conversationId, userId } = req.body;

    const deletedMessage = await MessageService.deleteMessage({
      conversationId,
      msgId,
      userId,
    });

    req.io.to(`user:${userId}`).emit("tin_nhan_da_xoa", deletedMessage);

    res.status(200).json(deletedMessage);
  } catch (error) {
    if (
      error.message === "Tin nhắn không tồn tại" ||
      error.message === "Bạn không có quyền xóa tin nhắn này" ||
      error.message === "Bạn không thuộc cuộc hội thoại này"
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

    const result = await MessageService.pinMessage({
      conversationId,
      msgId,
      userId,
      isPinned,
    });

    // Emit to all participants
    const participants =
      await ParticipantService.getParticipants(conversationId);
    participants.forEach((p) => {
      req.io
        .to(`user:${p.user_id}`)
        .emit("tin_nhan_pin", result.updatedMessage);
      if (result.systemMessage) {
        req.io.to(`user:${p.user_id}`).emit("tin_nhan", result.systemMessage);
      }
    });

    res.status(200).json(result);
  } catch (error) {
    if (
      error.message === "Tin nhắn không tồn tại" ||
      error.message === "Mỗi đoạn chat chỉ được ghim tối đa 3 tin nhắn" ||
      error.message === "Không thể ghim tin nhắn đã bị xóa hoặc thu hồi"
    ) {
      return res.status(400).json({ error: error.message });
    }
    res.status(500).json({ error: error.message });
  }
};

// Get pinned messages
exports.getPinnedMessages = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId } = req.query;

    const messages = await MessageService.getPinnedMessages(
      conversationId,
      userId,
    );

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
      parseInt(skip),
    );

    res.status(200).json(messages);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Get media gallery items (flattened images/videos with pagination by media items)
exports.getMediaGallery = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { limit = 20, skip = 0 } = req.query;

    const payload = await MessageService.getMediaGallery(
      conversationId,
      parseInt(limit, 10),
      parseInt(skip, 10),
    );

    res.status(200).json(payload);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Get media messages around a specific media message
exports.getMediaAroundTarget = async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { messageId, before = 10, after = 10 } = req.query;

    if (!messageId) {
      return res.status(400).json({ error: "Thiếu messageId" });
    }

    const messages = await MessageService.getMediaAroundTarget(
      conversationId,
      String(messageId),
      parseInt(before, 10),
      parseInt(after, 10),
    );

    res.status(200).json(messages);
  } catch (error) {
    if (error.message === "Không tìm thấy media mục tiêu") {
      return res.status(404).json({ error: error.message });
    }
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
      parseInt(skip),
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
      parseInt(skip),
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
