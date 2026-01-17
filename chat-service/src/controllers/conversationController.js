const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type, memberIds } = req.body;
    let role = "user";
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
    });

    if (type === "group") {
      role = "admin";
    }

    await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: creatorId,
      role: role,
    });

    if (memberIds && Array.isArray(memberIds) && memberIds.length > 0) {
      await Promise.all(
        memberIds.map(async (userId) => {
          const member = await ParticipantService.addParticipant({
            conversationId: conversation._id,
            userId: userId,
            role: "user",
          });

          req.io.to(conversation._id).emit("them_nguoi_moi", member);

          console.log(
            `${userId} da duoc them vao phong ${conversation._id} o database`,
          );

          return member;
        }),
      );
    }

    req.io.emit("tao_phong_moi", conversation);
    console.log(`Phong ${conversation._id} moi duoc tao ra o database`);

    res.status(200).json(conversation);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};




