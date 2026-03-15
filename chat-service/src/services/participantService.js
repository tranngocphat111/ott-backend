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

exports.updateConversationCategory = async (conversationId, userId, categoryId) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    { "settings.category_id": categoryId },
    { new: true }
  );
};

exports.updateNotificationStatus = async (conversationId, userId, status, muteUntil) => {
  const updateData = {
    "settings.notification_status": status,
  };
  
  if (muteUntil) {
    updateData["settings.mute_until"] = muteUntil;
  }
  
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    updateData,
    { new: true }
  );
};

exports.updatePinStatus = async (conversationId, userId, isPinned) => {
  return await Participant.findOneAndUpdate(
    { conversation_id: conversationId, user_id: userId },
    {
      "settings.is_pinned": isPinned,
      "settings.pinned_at": isPinned ? new Date() : null,
    },
    { new: true }
  );
};


