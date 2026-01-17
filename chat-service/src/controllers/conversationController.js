const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type } = req.body;
    let role = "user";
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
    });

    if (type === "group") {
      role == "admin";
    }

    await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: creatorId,
      role: role,
    });

    req.io.emit("tao_phong_moi", conversation);
    console.log(`Phong ${conversation._id} moi duoc tao ra o database`);

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

    req.io.to(conversationId).emit("them_nguoi_moi", member);
    console.log(
      `${userId} da duoc them vao phong ${conversationId} o database`,
    );

    res.status(200).json(member);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};


