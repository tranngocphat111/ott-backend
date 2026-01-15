const Message = require("../models/Message");
const ConversationService = require("./conversationService");

exports.sendMessage = async ({ conversationId, senderId, content, type }) => {
  const newMessage = new Message({
    conversation_id: conversationId,
    sender_id: senderId,
    content: [content],
    type: type,
  });

  const savedMessage = await newMessage.save();

  await ConversationService.updateLastMessage(conversationId, savedMessage);

  return savedMessage;
};

exports.getMessageHistory = async (conversationId) => {
  return await Message.find({ conversation_id: conversationId }).sort({
    msg_id: 1,
  });
};
