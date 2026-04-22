const Conversation = require("../models/Conversation");
const User = require("../models/User");
const Participant = require("../models/Participant");
const Message = require("../models/Message");

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

exports.findOrCreatePrivateConversation = async (user1Id, user2Id) => {
  const Participant = require("../models/Participant");
  
  // Tìm các participant của user1
  const p1 = await Participant.find({ user_id: user1Id }).lean();
  const conv1Ids = p1.map(p => String(p.conversation_id));
  
  // Tìm tất cả các participant của user2 mà thuộc các conversation của user1
  const commonParticipants = await Participant.find({
    user_id: user2Id,
    conversation_id: { $in: conv1Ids }
  }).populate("conversation_id").lean();

  // Tìm cuộc hội thoại type 'private' trong số các cuộc hội thoại chung
  const privateParticipant = commonParticipants.find(p => 
    p.conversation_id && 
    p.conversation_id.type === "private" && 
    !p.conversation_id.is_self_conversation
  );

  if (privateParticipant) {
    return privateParticipant.conversation_id;
  }

  // Nếu không thấy, tạo mới
  const conversation = await exports.createConversation({
    creatorId: user1Id,
    type: "private",
    memberCount: 2
  });

  const ParticipantService = require("./participantService");
  await ParticipantService.addParticipant({
    conversationId: conversation._id,
    userId: user1Id,
    role: "user"
  });
  await ParticipantService.addParticipant({
    conversationId: conversation._id,
    userId: user2Id,
    role: "user"
  });

  return conversation;
};

exports.getAllConversations = async () => {
  return await Conversation.find().sort({ updatedAt: -1 });
};
exports.updateLastMessage = async (conversationId, message) => {
  const rawType = String(message?.type || "text");
  const safeType = rawType; // Preserve system type for frontend
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
    case "poll":
      displayContent = `[Bình chọn] ${message.poll_question || "Khảo sát mới"}`;
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
        type: safeType,
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

  if (updateData.requesterId) {
    const participant = await Participant.findOne({
      conversation_id: conversationId,
      user_id: updateData.requesterId,
    })
      .select("roles")
      .lean();

    if (!participant) {
      throw new Error("Chỉ thành viên nhóm mới có quyền cập nhật nhóm");
    }
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

exports.dissolveGroup = async (conversationId, requesterId) => {
  const conversation = await Conversation.findById(conversationId).lean();

  if (!conversation) {
    throw new Error("Cuộc hội thoại không tồn tại");
  }

  if (conversation.type !== "group") {
    throw new Error("Chỉ trưởng nhóm mới có thể giải tán nhóm");
  }

  if (String(conversation.created_by) !== String(requesterId)) {
    throw new Error("Chỉ trưởng nhóm mới có thể giải tán nhóm");
  }

  const participants = await Participant.find({ conversation_id: conversationId })
    .select("user_id")
    .lean();

  const affectedUserIds = participants
    .map((item) => item.user_id)
    .filter(Boolean);

  const { DeleteObjectCommand } = require("@aws-sdk/client-s3");
  const { s3Client, bucketName } = require("../config/s3");
  const messageCacheService = require("./messageCacheService");

  // Get messages to extract S3 keys before deleting
  const messages = await Message.find({ conversation_id: conversationId }).lean();
  const keysToDelete = [];
  
  messages.forEach(msg => {
    if (['image', 'video', 'audio', 'file'].includes(msg.type) && Array.isArray(msg.content)) {
      msg.content.forEach(key => {
        if (key && !/^(https?:\/\/|www\.|data:)/i.test(key)) {
          keysToDelete.push(key);
        }
      });
    }
  });

  if (keysToDelete.length > 0) {
    try {
      await Promise.all(keysToDelete.map(key => 
        s3Client.send(new DeleteObjectCommand({ Bucket: bucketName, Key: key }))
      ));
    } catch (err) {
      console.error("Lỗi xóa file S3 khi giải tán nhóm:", err);
    }
  }

  const messageResult = await Message.deleteMany({ conversation_id: conversationId });
  await messageCacheService.clearCache(conversationId);

  const finalNotice = await Message.create({
    conversation_id: conversationId,
    sender_id: requesterId,
    type: "system_leave",
    content: ["Nhóm đã được giải tán"],
    system_meta: {
      action: "group_dissolved",
      dissolved_by: requesterId,
      show_delete_for_non_owner: true,
    },
  });

    // Add owner to deleted_for so they don't see the final message
    await Message.findByIdAndUpdate(
      finalNotice._id,
      { $addToSet: { deleted_for: requesterId } },
    );

  await exports.updateLastMessage(conversationId, finalNotice);

  await Conversation.findByIdAndUpdate(conversationId, {
    status: "dissolved",
    is_dissolved: true
  });

  await Participant.updateMany(
    { conversation_id: conversationId },
    {
      $set: {
        "settings.group_dissolved_at": new Date(),
        "settings.group_dissolved_by": requesterId,
      },
    },
  );

  // Hide conversation for owner (soft-delete style)
  if (finalNotice && finalNotice.msg_id) {
    await Participant.findOneAndUpdate(
      { conversation_id: conversationId, user_id: requesterId },
      { $set: { deleted_msg_id: finalNotice.msg_id } }
    );
  }

  return {
    success: true,
    conversationId,
      ownerId: requesterId,
    affectedUserIds,
    deletedMessages: messageResult.deletedCount || 0,
    deletedParticipants: 0,
    finalNotice,
  };
};
