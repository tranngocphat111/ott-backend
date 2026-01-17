const Participant = require("../models/Participant");

exports.addParticipant = async ({ conversationId, userId, role }) => {
  const existing = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (existing) {
    console.log(`User ${userId} co trong nhom roi ban ehhhh.`);
    return existing;
  }

  const newMember = new Participant({
    conversation_id: conversationId,
    user_id: userId,
    roles: role,
  });

  return await newMember.save();
};

exports.getConversationsByUserId = async (userId) => {
  return await Participant.find({ user_id: userId })
    .populate("conversation_id")
    .sort({ updatedAt: -1 });
};

exports.getParticipants = async (conversationId) => {
  return await Participant.find({ conversation_id: conversationId });
};

exports.updateLastRead = async (conversationId, userId, msgId) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    {
      last_read_message_id: msgId,
      last_read_at: new Date(),
    },
    { new: true }
  );
};


