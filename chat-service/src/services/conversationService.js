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
  let displayContent = "";

  switch (message.type) {
    case "image":
      displayContent = "[Hình ảnh]";
      break;
    case "video":
      displayContent = "[Video]";
      break;
    case "file":
      displayContent = "[Tệp tin]";
      break;
    default:
      const rawContent = message.content[0] || "";
      displayContent =
        rawContent.length > 50
          ? rawContent.substring(0, 50) + "..."
          : rawContent;
      break;
  }

  return await Conversation.findByIdAndUpdate(
    conversationId,
    {
      last_message: {
        msg_id: message.msg_id,
        sender_id: message.sender_id,
        content: displayContent,
        type: message.type,
        createdAt: message.createdAt,
      },
    },
    { new: true },
  );
};

exports.getConversationById = async (conversationId) => {
  return await Conversation.findById(conversationId);
};
