const ParticipantService = require("../services/participantService");

exports.getConversationsByUserId = async (req, res) => {
  try {
    const { userId } = req.params;

    const participants = await ParticipantService.getConversationsByUserId(userId);
    
    // Map dữ liệu để frontend dễ sử dụng
    const conversations = participants.map(participant => {
      const conversation = participant.conversation_id;
      
      // Nếu conversation không tồn tại, bỏ qua
      if (!conversation) return null;
      
      return {
        ...conversation.toObject(),
        // Giữ lại settings của participant
        is_pinned: participant.settings?.is_pinned || false,
        is_muted: participant.settings?.notification_status === 'mute',
      };
    }).filter(conv => conv !== null); // Loại bỏ null

    res.status(200).json(conversations);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateConversationCategory = async (req, res) => {
  try {
    const { conversationId, userId, categoryId } = req.body;
    const participant = await ParticipantService.updateConversationCategory(
      conversationId,
      userId,
      categoryId
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updateNotificationStatus = async (req, res) => {
  try {
    const { conversationId, userId, status, muteUntil } = req.body;
    const participant = await ParticipantService.updateNotificationStatus(
      conversationId,
      userId,
      status,
      muteUntil
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.updatePinStatus = async (req, res) => {
  try {
    const { conversationId, userId, isPinned } = req.body;
    const participant = await ParticipantService.updatePinStatus(
      conversationId,
      userId,
      isPinned
    );
    res.status(200).json(participant);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
