const ParticipantService = require("../services/participantService");

exports.getConversationsByUserId = async (req, res) => {
  try {
    const { userId } = req.params;

    const participants = await ParticipantService.getConversationsByUserId(userId);
    // Trả về đầy đủ participant data + conversation data
    const result = participants.map(participant => {
      const conversation = participant.conversation_id;
      
      // Nếu conversation không tồn tại, bỏ qua
      if (!conversation) return null;
      
      return {
        conversation: conversation.toObject(),
        participant: {
          _id: participant._id,
          user_id: participant.user_id,
          conversation_id: participant.conversation_id._id,
          settings: participant.settings,
          last_read_message_id: participant.last_read_message_id,
          last_read_at: participant.last_read_at,
          deleted_msg_id: participant.deleted_msg_id,
          nickname: participant.nickname,
          joined_at: participant.joined_at,
          roles: participant.roles,
        }
      };
    }).filter(item => item !== null);

    res.status(200).json(result);
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
