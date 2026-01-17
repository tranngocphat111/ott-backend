const ParticipantService = require("../services/participantService");

exports.getConversationsByUserId = async (req, res) => {
  try {
    const { userId } = req.params;

    const data = await ParticipantService.getConversationsByUserId(userId);

    res.status(200).json(data);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
