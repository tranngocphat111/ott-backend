const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type } = req.body;
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
    });
    res.status(201).json(conversation);
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
    res.status(200).json(member);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
