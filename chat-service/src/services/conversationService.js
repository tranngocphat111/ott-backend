const Conversation = require("../models/Conversation");
const User = require("../models/User");

exports.createConversation = async ({
  creatorId,
  type,
  name,
  avatar,
  memberCount,
}) => {
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
    case "audio":
      displayContent = "[Âm thanh]";
      break;
    default: {
      const rawContent = message.content[0] || "";
      displayContent =
        rawContent.length > 50
          ? rawContent.substring(0, 50) + "..."
          : rawContent;
      break;
    }
  }

  const sender = await User.findOne({ user_id: message.sender_id })
    .select("name")
    .lean();

  return await Conversation.findByIdAndUpdate(
    conversationId,
    {
      last_message: {
        msg_id: message.msg_id,
        sender_id: message.sender_id,
        sender_name: sender?.name || "",
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

// Update conversation name/avatar
exports.updateConversation = async (conversationId, updateData) => {
  const conversation = await Conversation.findById(conversationId);
  
  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ có thể cập nhật thông tin nhóm chat");
  }

  const allowedFields = ["name", "avatar", "background"];
  const filteredData = {};
  
  for (const field of allowedFields) {
    if (updateData[field] !== undefined) {
      filteredData[field] = updateData[field];
    }
  }

  return await Conversation.findByIdAndUpdate(
    conversationId,
    filteredData,
    { new: true }
  );
};
