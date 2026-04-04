const Message = require("../models/Message");
const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");
const User = require("../models/User");
const ConversationService = require("./conversationService");
const messageCacheService = require("./messageCacheService");
const { PutObjectCommand } = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
const { s3Client, bucketName } = require("../config/s3");

const crypto = require("crypto");

const buildReplyPreview = (message) => {
  if (!message) return null;

  const rawContent = Array.isArray(message.content)
    ? message.content[0] || ""
    : message.content || "";

  let preview = "";
  switch (message.type) {
    case "image":
      preview = "[Hình ảnh]";
      break;
    case "video":
      preview = "[Video]";
      break;
    case "file":
      preview = "[Tệp tin]";
      break;
    case "audio":
      preview = "[Âm thanh]";
      break;
    default:
      preview = rawContent;
      break;
  }

  return {
    msg_id: message.msg_id,
    sender_id: message.sender_id,
    type: message.type,
    content: preview.length > 120 ? preview.substring(0, 120) + "..." : preview,
    is_deleted: !!message.is_deleted,
    is_revoked: !!message.is_revoked,
  };
};

exports.generatePresignedUrl = async (fileName, fileType) => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  const fileCategory = fileType.startsWith("image/")
    ? "image"
    : fileType.startsWith("video/")
      ? "video"
      : fileType.startsWith("audio/")
        ? "audio"
        : "file";

  const uniqueId = crypto.randomBytes(8).toString("hex");

  const key = `messages/${fileCategory}/${year}/${month}/${day}/${uniqueId}_${fileName}`;

  const command = new PutObjectCommand({
    Bucket: bucketName,
    Key: key,
    ContentType: fileType,
  });

  const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 300 });

  return { uploadUrl, fileCategory, key };
};

exports.sendMessage = async ({
  conversationId,
  senderId,
  content,
  type,
  size,
  replyToMsgId,
}) => {
  // Nếu content đã là array (image keys) thì dùng trực tiếp, không thì wrap
  const contentArray = Array.isArray(content) ? content : [content];

  let replyMessage = null;
  if (replyToMsgId) {
    replyMessage = await Message.findOne({
      msg_id: replyToMsgId,
      conversation_id: conversationId,
    }).lean();

    if (!replyMessage) {
      throw new Error("Tin nhắn trả lời không hợp lệ");
    }
  }

  const newMessage = new Message({
    conversation_id: conversationId,
    sender_id: senderId,
    content: contentArray,
    type: type,
    size: size,
    reply_to_msg_id: replyToMsgId || null,
  });

  const savedMessage = await newMessage.save();
  const updatedConversation = await ConversationService.updateLastMessage(
    conversationId,
    savedMessage,
  );

  // Add message to Redis cache (last 20 messages)
  await messageCacheService.addMessage(conversationId, {
    ...savedMessage.toObject(),
    sender_name: updatedConversation?.last_message?.sender_name || "",
  });

  // Gửi kèm sender_name để FE cập nhật conversation list mà không cần query thêm
  return {
    ...savedMessage.toObject(),
    sender_name: updatedConversation?.last_message?.sender_name || "",
    reply_to: buildReplyPreview(replyMessage),
  };
};

exports.getMessageHistory = async (conversationId, deletedMsgId = "0") => {
  const messages = await Message.find({ conversation_id: conversationId }).sort(
    {
      msg_id: 1,
    },
  );

  const filteredMessages =
    !deletedMsgId || deletedMsgId === "0"
      ? messages
      : messages.filter((m) => BigInt(m.msg_id) > BigInt(deletedMsgId));

  const replyIds = [
    ...new Set(
      filteredMessages
        .map((m) => m.reply_to_msg_id)
        .filter((replyId) => typeof replyId === "string" && replyId.length > 0),
    ),
  ];

  if (replyIds.length === 0) {
    return filteredMessages.map((m) => ({ ...m.toObject(), reply_to: null }));
  }

  const referencedMessages = await Message.find({
    conversation_id: conversationId,
    msg_id: { $in: replyIds },
  }).lean();

  const referencedMap = new Map(
    referencedMessages.map((m) => [m.msg_id, buildReplyPreview(m)]),
  );

  return filteredMessages.map((m) => ({
    ...m.toObject(),
    reply_to: referencedMap.get(m.reply_to_msg_id) || null,
  }));
};

exports.reactToMessage = async ({
  conversationId,
  msgId,
  userId,
  reactionType,
}) => {
  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message) {
    throw new Error("Tin nhắn không tồn tại");
  }

  const normalizedReaction = String(reactionType || "").trim();
  if (!normalizedReaction) {
    throw new Error("Reaction không hợp lệ");
  }

  const existingReactionIndex = message.reactions.findIndex(
    (reaction) =>
      reaction.user_id === userId && reaction.type === normalizedReaction,
  );

  if (existingReactionIndex >= 0) {
    // Bấm lại cùng emoji thì bỏ reaction đó.
    message.reactions.splice(existingReactionIndex, 1);
  } else {
    // Cho phép 1 user có nhiều emoji reaction trên cùng 1 tin nhắn.
    message.reactions.push({
      user_id: userId,
      type: normalizedReaction,
    });
  }

  const updatedMessage = await message.save();

  return {
    _id: updatedMessage._id,
    msg_id: updatedMessage.msg_id,
    conversation_id: updatedMessage.conversation_id,
    reactions: updatedMessage.reactions,
  };
};

// Pin/Unpin message
exports.pinMessage = async ({ conversationId, msgId, userId, isPinned }) => {
  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message) {
    throw new Error("Tin nhắn không tồn tại");
  }

  message.is_pinned = isPinned;
  message.pinned_at = isPinned ? new Date() : null;
  message.pinned_by = isPinned ? userId : null;

  const updatedMessage = await message.save();

  return {
    _id: updatedMessage._id,
    msg_id: updatedMessage.msg_id,
    conversation_id: updatedMessage.conversation_id,
    is_pinned: updatedMessage.is_pinned,
    pinned_at: updatedMessage.pinned_at,
    pinned_by: updatedMessage.pinned_by,
    type: updatedMessage.type,
    content: updatedMessage.content,
    sender_id: updatedMessage.sender_id,
    createdAt: updatedMessage.createdAt,
  };
};

// Get pinned messages for a conversation
exports.getPinnedMessages = async (conversationId) => {
  const messages = await Message.find({
    conversation_id: conversationId,
    is_pinned: true,
  }).sort({ pinned_at: -1 });

  return messages;
};

// Get media messages (images/videos) for a conversation
exports.getMediaMessages = async (conversationId, limit = 20, skip = 0) => {
  const messages = await Message.find({
    conversation_id: conversationId,
    type: { $in: ["image", "video"] },
    is_deleted: false,
    is_revoked: false,
  })
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);

  return messages;
};

// Get file messages for a conversation
exports.getFileMessages = async (conversationId, limit = 20, skip = 0) => {
  const messages = await Message.find({
    conversation_id: conversationId,
    type: "file",
    is_deleted: false,
    is_revoked: false,
  })
    .sort({ createdAt: -1 })
    .skip(skip)
    .limit(limit);

  return messages;
};

// Extract and get links from messages
exports.getLinkMessages = async (conversationId, limit = 20, skip = 0) => {
  // URL regex pattern
  const urlPattern = /https?:\/\/[^\s<>"{}|\\^`[\]]+/gi;

  const messages = await Message.find({
    conversation_id: conversationId,
    type: "text",
    is_deleted: false,
    is_revoked: false,
  }).sort({ createdAt: -1 });

  // Filter messages that contain links
  const messagesWithLinks = messages.filter((msg) => {
    const content = Array.isArray(msg.content) ? msg.content.join(" ") : msg.content;
    return urlPattern.test(content);
  });

  // Extract links from messages
  const linksData = messagesWithLinks.slice(skip, skip + limit).map((msg) => {
    const content = Array.isArray(msg.content) ? msg.content.join(" ") : msg.content;
    const links = content.match(urlPattern) || [];
    
    return {
      _id: msg._id,
      msg_id: msg.msg_id,
      links: links,
      sender_id: msg.sender_id,
      createdAt: msg.createdAt,
    };
  });

  return linksData;
};

const escapeRegex = (value = "") =>
  String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const getMessagePreview = (message) => {
  const raw = Array.isArray(message.content)
    ? String(message.content[0] || "")
    : String(message.content || "");

  if (message.type === "image") return "[Hình ảnh]";
  if (message.type === "video") return "[Video]";
  if (message.type === "file") return "[Tệp tin]";
  if (message.type === "audio") return "[Âm thanh]";

  return raw.length > 160 ? `${raw.substring(0, 160)}...` : raw;
};

exports.searchEverything = async ({
  userId,
  keyword,
  limit = 20,
  senderId,
}) => {
  const safeLimit = Number.isFinite(Number(limit))
    ? Math.max(1, Math.min(Number(limit), 50))
    : 20;
  const query = String(keyword || "").trim();

  if (!userId || !query) {
    return {
      contacts: [],
      conversations: [],
      messages: [],
      files: [],
      media: [],
      total: 0,
    };
  }

  const queryRegex = new RegExp(escapeRegex(query), "i");

  const myParticipants = await Participant.find({ user_id: userId })
    .select("conversation_id")
    .lean();
  const conversationIds = myParticipants
    .map((item) => item.conversation_id)
    .filter(Boolean);

  if (!conversationIds.length) {
    return {
      contacts: [],
      conversations: [],
      messages: [],
      files: [],
      media: [],
      total: 0,
    };
  }

  const [conversations, participantsInScope] = await Promise.all([
    Conversation.find({ _id: { $in: conversationIds }, is_deleted: false })
      .sort({ updatedAt: -1 })
      .lean(),
    Participant.find({
      conversation_id: { $in: conversationIds },
      user_id: { $ne: userId },
    })
      .select("conversation_id user_id")
      .lean(),
  ]);

  const userIds = [...new Set(participantsInScope.map((p) => p.user_id).filter(Boolean))];
  const users = await User.find({ user_id: { $in: userIds } })
    .select("user_id name avatar")
    .lean();
  const userMap = new Map(users.map((u) => [u.user_id, u]));

  const userConversationMap = new Map();
  participantsInScope.forEach((p) => {
    const key = String(p.user_id || "");
    if (!key) return;
    if (!userConversationMap.has(key)) userConversationMap.set(key, []);
    userConversationMap.get(key).push(String(p.conversation_id));
  });

  const contacts = users
    .filter(
      (u) => queryRegex.test(String(u.name || "")) || queryRegex.test(String(u.user_id || "")),
    )
    .slice(0, safeLimit)
    .map((u) => ({
      user_id: u.user_id,
      name: u.name || u.user_id,
      avatar: u.avatar || "",
      phone: u.user_id,
      conversation_ids: userConversationMap.get(u.user_id) || [],
    }));

  const convById = new Map(conversations.map((c) => [String(c._id), c]));
  const privateConvIdsByMatchedContact = new Set();
  contacts.forEach((contact) => {
    (contact.conversation_ids || []).forEach((id) => {
      const conv = convById.get(String(id));
      if (conv?.type === "private") {
        privateConvIdsByMatchedContact.add(String(id));
      }
    });
  });

  const conversationResults = conversations
    .filter((conv) => {
      if (conv.type === "group" && queryRegex.test(String(conv.name || ""))) {
        return true;
      }
      if (privateConvIdsByMatchedContact.has(String(conv._id))) {
        return true;
      }
      return false;
    })
    .slice(0, safeLimit)
    .map((conv) => ({
      conversation_id: String(conv._id),
      type: conv.type,
      name: conv.name || "",
      avatar: conv.avatar || "",
      member_count: conv.member_count || 0,
      updatedAt: conv.updatedAt,
      last_message: conv.last_message || null,
    }));

  const messageFilter = {
    conversation_id: { $in: conversationIds },
    is_deleted: false,
    is_revoked: false,
  };

  if (senderId) {
    messageFilter.sender_id = senderId;
  }

  const [matchedMessages, matchedFiles, matchedMedia] = await Promise.all([
    Message.find({
      ...messageFilter,
      type: { $in: ["text", "link"] },
      content: { $elemMatch: { $regex: queryRegex } },
    })
      .sort({ createdAt: -1 })
      .limit(safeLimit)
      .lean(),
    Message.find({
      ...messageFilter,
      type: "file",
      content: { $elemMatch: { $regex: queryRegex } },
    })
      .sort({ createdAt: -1 })
      .limit(safeLimit)
      .lean(),
    Message.find({
      ...messageFilter,
      type: { $in: ["image", "video"] },
      content: { $elemMatch: { $regex: queryRegex } },
    })
      .sort({ createdAt: -1 })
      .limit(safeLimit)
      .lean(),
  ]);

  const senderIdsInResults = [
    ...new Set(
      [...matchedMessages, ...matchedFiles, ...matchedMedia]
        .map((msg) => String(msg.sender_id || ""))
        .filter(Boolean),
    ),
  ];

  if (senderIdsInResults.length > 0) {
    const senderUsers = await User.find({ user_id: { $in: senderIdsInResults } })
      .select("user_id name avatar")
      .lean();

    senderUsers.forEach((user) => {
      userMap.set(user.user_id, user);
    });
  }

  const messages = matchedMessages.map((msg) => {
    const sender = userMap.get(String(msg.sender_id));
    return {
      _id: String(msg._id),
      msg_id: msg.msg_id,
      conversation_id: String(msg.conversation_id),
      sender_id: msg.sender_id,
      sender_name: sender?.name || msg.sender_id,
      sender_avatar: sender?.avatar || "",
      type: msg.type,
      preview: getMessagePreview(msg),
      createdAt: msg.createdAt,
    };
  });

  const files = matchedFiles.flatMap((msg) => {
    const sender = userMap.get(String(msg.sender_id));
    const keys = Array.isArray(msg.content) ? msg.content : [msg.content];

    return keys
      .filter((key) => !!key && queryRegex.test(String(key)))
      .map((key, index) => {
        const rawName = String(key).split("/").pop() || "File";
        const match = rawName.match(/^[a-f0-9]+_(.+)$/i);
        const fileName = match ? match[1] : rawName;
        return {
          _id: `${msg._id}:file:${index}`,
          msg_id: msg.msg_id,
          message_id: String(msg._id),
          conversation_id: String(msg.conversation_id),
          sender_id: msg.sender_id,
          sender_name: sender?.name || msg.sender_id,
          key: String(key),
          file_name: fileName,
          createdAt: msg.createdAt,
        };
      });
  });

  const media = matchedMedia.flatMap((msg) => {
    const sender = userMap.get(String(msg.sender_id));
    const keys = Array.isArray(msg.content) ? msg.content : [msg.content];

    return keys
      .filter((key) => !!key && queryRegex.test(String(key)))
      .map((key, index) => ({
        _id: `${msg._id}:media:${index}`,
        msg_id: msg.msg_id,
        message_id: String(msg._id),
        conversation_id: String(msg.conversation_id),
        sender_id: msg.sender_id,
        sender_name: sender?.name || msg.sender_id,
        key: String(key),
        media_type: msg.type,
        createdAt: msg.createdAt,
      }));
  });

  return {
    contacts,
    conversations: conversationResults,
    messages,
    files: files.slice(0, safeLimit),
    media: media.slice(0, safeLimit),
    total:
      contacts.length +
      conversationResults.length +
      messages.length +
      files.length +
      media.length,
  };
};
