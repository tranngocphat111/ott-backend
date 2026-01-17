const Participant = require("../models/Participant");
const ConversationService = require("../services/conversationService");
const ParticipantService = require("../services/participantService");

exports.createConversation = async (req, res) => {
  try {
    const { creatorId, type } = req.body;
    const conversation = await ConversationService.createConversation({
      creatorId,
      type,
    });

    await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: creatorId,
      role: "admin",
    });

    req.io.emit("tao_phong_moi", conversation);

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

    res.status(200).json(member);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.accessConversation = async (req, res) => {
  try {
    const { userId, targetId } = req.body; 

    const myConvs = await Participant.find({ user_id: userId }).distinct(
      "conversation_id"
    );

    const existingChat = await Participant.findOne({
      user_id: targetId,
      conversation_id: { $in: myConvs },
    }).populate("conversation_id");

    if (existingChat && existingChat.conversation_id.type === "private") {
      return res.status(200).json(existingChat.conversation_id);
    }

    const newConv = await ConversationService.createConversation({
      creatorId: userId,
      type: "private",
    });

    await Promise.all([
      ParticipantService.addParticipant({
        conversationId: newConv._id,
        userId: userId,
        role: "admin",
      }),
      ParticipantService.addParticipant({
        conversationId: newConv._id,
        userId: targetId,
        role: "user",
      }),
    ]);

    req.io.emit("tao_phong_moi", newConv);

    return res.status(200).json(newConv);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
