const Conversation = require("../models/Conversation");

exports.createConversation = async ({ creatorId, type, name, avatar, memberCount }) => {
  const newConversation = new Conversation({
    type: type,
    name: name || "",
    avatar: avatar || "",
    created_by: creatorId,
    member_count: memberCount || 2,
    is_deleted: false,
    background: "",
  });

  return await newConversation.save();
};

exports.getAllConversations = async () => {
  return await Conversation.find().sort({ updatedAt: -1 });
};

exports.updateLastMessage = async (conversationId, message) => {
  return await Conversation.findByIdAndUpdate(
    conversationId,
    {
      last_message: {
        msg_id: message.msg_id,
        sender_id: message.sender_id,
        content: message.content[0],
        type: message.type,
        createdAt: message.createdAt,
      },
    },
    { new: true }
  );
};

exports.getConversationById = async (conversationId) => {
  return await Conversation.findById(conversationId);
};
