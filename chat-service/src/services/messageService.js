const Message = require("../models/Message");
const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");
const User = require("../models/User");
const ConversationService = require("./conversationService");
const messageCacheService = require("./messageCacheService");
const {
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
  CopyObjectCommand,
} = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
const { s3Client, bucketName } = require("../config/s3");

const fs = require("fs/promises");
const path = require("path");
const os = require("os");
const { spawn } = require("child_process");
const { pipeline } = require("stream/promises");
const crypto = require("crypto");

const REVOKED_PLACEHOLDER = "Tin nhắn đã được thu hồi";

const sanitizeAvatarValue = (value) => {
  const avatar = String(value || "").trim();
  if (!avatar) return "";
  if (/^data:image\//i.test(avatar)) return "";
  return avatar;
};

const getFileNameFromKey = (key) => {
  const rawName =
    String(key || "")
      .split("/")
      .pop() || "File";
  const match = rawName.match(/^[a-f0-9]+_(.+)$/i);
  return match ? match[1] : rawName;
};

const getFileExtension = (value) => {
  const fileName = getFileNameFromKey(value);
  const match = fileName.match(/\.([a-z0-9]+)$/i);
  return match ? match[1].toLowerCase() : "";
};

const isWebVoiceAudioKey = (key) => {
  const extension = getFileExtension(key);
  return ["webm", "ogg", "opus"].includes(extension);
};

const replaceKeyExtension = (key, extension) => {
  const normalizedKey = String(key || "");
  if (!normalizedKey) return normalizedKey;

  return normalizedKey.replace(/\.[^.\/]+$/i, `.${extension}`);
};

const runFfmpegTranscode = (inputPath, outputPath) => {
  const ffmpegPath = process.env.FFMPEG_PATH || "ffmpeg";

  return new Promise((resolve, reject) => {
    const child = spawn(
      ffmpegPath,
      ["-y", "-i", inputPath, "-vn", "-c:a", "aac", "-b:a", "128k", outputPath],
      { stdio: ["ignore", "ignore", "pipe"] },
    );

    let stderr = "";
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });

    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
        return;
      }

      reject(new Error(stderr || `ffmpeg exited with code ${code}`));
    });
  });
};

const ensureDirectory = async (directoryPath) => {
  await fs.mkdir(directoryPath, { recursive: true });
  return directoryPath;
};

const downloadS3ObjectToFile = async (key, filePath) => {
  const response = await s3Client.send(
    new GetObjectCommand({ Bucket: bucketName, Key: key }),
  );

  if (!response?.Body) {
    throw new Error("Không thể tải tệp từ S3");
  }

  await pipeline(response.Body, require("fs").createWriteStream(filePath));
};

const uploadFileToS3 = async (key, filePath, contentType) => {
  const fileBuffer = await fs.readFile(filePath);

  await s3Client.send(
    new PutObjectCommand({
      Bucket: bucketName,
      Key: key,
      Body: fileBuffer,
      ContentType: contentType,
    }),
  );
};

const copyS3Object = async (sourceKey) => {
  if (!sourceKey) return sourceKey;

  const rawName = sourceKey.split('/').pop() || 'File';
  const match = rawName.match(/^[a-f0-9]+_(.+)$/i);
  const originalFileName = match ? match[1] : rawName;
  const uniqueId = crypto.randomBytes(8).toString('hex');
  const pathParts = sourceKey.split('/');
  pathParts.pop(); // Remove the old file name
  const basePath = pathParts.join('/');
  
  const newKey = basePath ? `${basePath}/${uniqueId}_${originalFileName}` : `${uniqueId}_${originalFileName}`;

  await s3Client.send(
    new CopyObjectCommand({
      Bucket: bucketName,
      CopySource: `${bucketName}/${sourceKey}`,
      Key: newKey,
    })
  );

  return newKey;
};

const maybeTranscodeVoiceKey = async (key) => {
  if (!key || !isWebVoiceAudioKey(key)) {
    return key;
  }

  const tempRoot = await ensureDirectory(
    path.join(os.tmpdir(), "ott-voice-transcode"),
  );
  const requestId = crypto.randomBytes(8).toString("hex");
  const inputPath = path.join(
    tempRoot,
    `${requestId}-input.${getFileExtension(key) || "webm"}`,
  );
  const outputPath = path.join(tempRoot, `${requestId}-output.m4a`);
  const outputKey = replaceKeyExtension(key, "m4a");

  try {
    await downloadS3ObjectToFile(key, inputPath);
    await runFfmpegTranscode(inputPath, outputPath);
    await uploadFileToS3(outputKey, outputPath, "audio/mp4");
    await s3Client.send(
      new DeleteObjectCommand({ Bucket: bucketName, Key: key }),
    );
    return outputKey;
  } catch (error) {
    console.warn(
      "Voice transcode skipped or failed, keeping original file:",
      error.message,
    );
    return key;
  } finally {
    await Promise.allSettled([fs.unlink(inputPath), fs.unlink(outputPath)]);
  }
};

const enrichMessageWithSender = async (message) => {
  const sender = await User.findOne({ user_id: message.sender_id })
    .select("name avatar")
    .lean();

  return {
    ...message,
    sender_name: sender?.name || message.sender_name || "",
    sender_avatar: sanitizeAvatarValue(
      sender?.avatar || message.sender_avatar || "",
    ),
  };
};

const isVisibleToUser = (message, userId) => {
  if (message.is_deleted) return false;
  if (!userId) return true;

  const deletedFor = Array.isArray(message.deleted_for)
    ? message.deleted_for
    : [];
  return !deletedFor.includes(userId);
};

const buildReplyPreview = (message, senderName = "") => {
  if (!message) return null;

  const rawContent = Array.isArray(message.content)
    ? message.content[0] || ""
    : message.content || "";
  const mediaUrls = Array.isArray(message.content)
    ? message.content.filter(Boolean).map((item) => String(item))
    : rawContent
      ? [String(rawContent)]
      : [];

  const isUrlLike = /^(https?:\/\/|www\.)/i.test(rawContent);
  const mediaUrl = rawContent && !isUrlLike ? rawContent : "";
  const fileName =
    message.type === "file" ||
    message.type === "video" ||
    message.type === "audio"
      ? getFileNameFromKey(rawContent)
      : "";

  let preview = "";
  switch (message.type) {
    case "image":
      preview = rawContent;
      break;
    case "video":
      preview = rawContent;
      break;
    case "file":
      preview = fileName || "[Tệp tin]";
      break;
    case "audio":
      preview = fileName || "[Âm thanh]";
      break;
    case "link":
      preview = rawContent;
      break;
    default:
      preview = rawContent;
      break;
  }

  return {
    msg_id: message.msg_id,
    sender_id: message.sender_id,
    sender_name: senderName || message.sender_name || "",
    type: message.type,
    content: preview.length > 120 ? preview.substring(0, 120) + "..." : preview,
    raw_content: rawContent,
    file_name: fileName,
    url: mediaUrl || rawContent,
    media_urls: message.type === "image" ? mediaUrls : undefined,
    media_count: message.type === "image" ? mediaUrls.length : undefined,
    is_deleted: !!message.is_deleted,
    is_revoked: !!message.is_revoked,
  };
};

const syncConversationLastMessageOnRevoke = async (conversationId, message) => {
  const conversation = await Conversation.findById(conversationId)
    .select("last_message")
    .lean();

  if (!conversation?.last_message?.msg_id) {
    return null;
  }

  if (String(conversation.last_message.msg_id) !== String(message.msg_id)) {
    return null;
  }

  const sender = await User.findOne({ user_id: message.sender_id })
    .select("name")
    .lean();

  const lastMessagePayload = {
    msg_id: message.msg_id,
    sender_id: message.sender_id,
    sender_name: sender?.name || conversation.last_message.sender_name || "",
    content: REVOKED_PLACEHOLDER,
    type: "text",
    createdAt: message.createdAt,
  };

  await Conversation.findByIdAndUpdate(conversationId, {
    last_message: lastMessagePayload,
  });

  return lastMessagePayload;
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
  const normalizedContent = [...contentArray];

  if (type === "audio" && normalizedContent.length > 0) {
    normalizedContent[0] = await maybeTranscodeVoiceKey(
      String(normalizedContent[0] || ""),
    );
  }

  let replyMessage = null;
  let replySender = null;
  if (replyToMsgId) {
    replyMessage = await Message.findOne({
      msg_id: replyToMsgId,
      conversation_id: conversationId,
    }).lean();

    if (!replyMessage) {
      throw new Error("Tin nhắn trả lời không hợp lệ");
    }

    replySender = await User.findOne({ user_id: replyMessage.sender_id })
      .select("name")
      .lean();
  }

  const newMessage = new Message({
    conversation_id: conversationId,
    sender_id: senderId,
    content: normalizedContent,
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
  const sender = await User.findOne({ user_id: senderId })
    .select("name avatar")
    .lean();
  const enrichedMessage = {
    ...savedMessage.toObject(),
    sender_name:
      sender?.name || updatedConversation?.last_message?.sender_name || "",
    sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
    reply_to: buildReplyPreview(replyMessage, replySender?.name || ""),
  };

  await messageCacheService.addMessage(conversationId, enrichedMessage);

  // Gửi kèm sender_name để FE cập nhật conversation list mà không cần query thêm
  return enrichedMessage;
};

exports.forwardMessage = async ({
  originalMsgId,
  conversationId,
  targetConversationIds,
  senderId,
}) => {
  const originalMessage = await Message.findOne({
    msg_id: originalMsgId,
    conversation_id: conversationId,
  }).lean();

  if (!originalMessage) {
    throw new Error("Tin nhắn gốc không tồn tại");
  }

  if (originalMessage.is_deleted || originalMessage.is_revoked) {
    throw new Error("Không thể chuyển tiếp tin nhắn đã bị xóa hoặc thu hồi");
  }

  const { type, content, size } = originalMessage;
  
  if (!["text", "link", "image", "video", "file", "audio"].includes(type)) {
    throw new Error("Loại tin nhắn này chưa hỗ trợ chuyển tiếp");
  }

  // Tiền xử lý nội dung để tạo bản sao độc lập nếu là file
  const originalContentArray = Array.isArray(content) ? content : [content];
  let forwardedContent = [...originalContentArray];

  // Nếu là file/media, cần copy S3 object để tin chuyển tiếp không phụ thuộc tin gốc
  if (["image", "video", "file", "audio"].includes(type)) {
    try {
      forwardedContent = await Promise.all(
        originalContentArray.map(async (key) => {
          if (!key) return key;
          // Loại trừ trường hợp url bên ngoài hoặc dữ liệu dạng base64/không phải key s3
          if (/^(https?:\/\/|www\.|data:)/i.test(key)) {
             return key;
          }
          return await copyS3Object(key);
        })
      );
    } catch (err) {
      console.error("Lỗi khi copy S3 object cho chuyển tiếp:", err);
      // Fallback: nếu lỗi copy, xài lại key gốc (dù đây là behavior cũ không mong muốn, nhưng giúp app ko crash)
      forwardedContent = [...originalContentArray];
    }
  }

  const results = [];
  
  // Gửi tin nhắn đến từng conversation đích
  for (const targetConversationId of targetConversationIds) {
    try {
      const savedMessage = await exports.sendMessage({
        conversationId: targetConversationId,
        senderId,
        content: forwardedContent,
        type,
        size: size || 0,
        replyToMsgId: null, // Tin chuyển tiếp không giữ reply context
      });
      results.push(savedMessage);
    } catch (error) {
      console.error(`Lỗi chuyển tiếp tin nhằn đến nhóm ${targetConversationId}:`, error);
    }
  }
  
  return results;
};

exports.getMessageHistory = async (
  conversationId,
  deletedMsgId = "0",
  userId,
) => {
  const messages = await Message.find({ conversation_id: conversationId }).sort(
    {
      msg_id: 1,
    },
  );

  const visibleMessages = messages.filter((m) => isVisibleToUser(m, userId));

  const filteredMessages =
    !deletedMsgId || deletedMsgId === "0"
      ? visibleMessages
      : visibleMessages.filter((m) => BigInt(m.msg_id) > BigInt(deletedMsgId));

  const replyIds = [
    ...new Set(
      filteredMessages
        .map((m) => m.reply_to_msg_id)
        .filter((replyId) => typeof replyId === "string" && replyId.length > 0),
    ),
  ];

  const senderIds = [
    ...new Set(
      filteredMessages.map((m) => String(m.sender_id || "")).filter(Boolean),
    ),
  ];
  const senders = senderIds.length
    ? await User.find({ user_id: { $in: senderIds } })
        .select("user_id name avatar")
        .lean()
    : [];
  const senderMap = new Map(
    senders.map((sender) => [String(sender.user_id || ""), sender]),
  );

  if (replyIds.length === 0) {
    return filteredMessages.map((m) => {
      const sender = senderMap.get(String(m.sender_id || ""));
      return {
        ...m.toObject(),
        sender_name: sender?.name || "",
        sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
        reply_to: null,
      };
    });
  }

  const referencedMessages = await Message.find({
    conversation_id: conversationId,
    msg_id: { $in: replyIds },
  }).lean();

  const referencedSenderIds = [
    ...new Set(referencedMessages.map((m) => m.sender_id)),
  ];
  const referencedSenders = await User.find({
    user_id: { $in: referencedSenderIds },
  })
    .select("user_id name")
    .lean();

  const senderNameMap = new Map(
    referencedSenders.map((user) => [user.user_id, user.name || ""]),
  );

  const referencedMap = new Map(
    referencedMessages.map((m) => [
      m.msg_id,
      buildReplyPreview(m, senderNameMap.get(m.sender_id) || ""),
    ]),
  );

  return filteredMessages.map((m) => ({
    ...m.toObject(),
    sender_name: senderMap.get(String(m.sender_id || ""))?.name || "",
    sender_avatar: sanitizeAvatarValue(
      senderMap.get(String(m.sender_id || ""))?.avatar || "",
    ),
    reply_to: referencedMap.get(m.reply_to_msg_id) || null,
  }));
};

exports.revokeMessage = async ({ conversationId, msgId, userId }) => {
  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message || message.is_deleted) {
    throw new Error("Tin nhắn không tồn tại");
  }

  if (message.sender_id !== userId) {
    throw new Error("Bạn không có quyền thu hồi tin nhắn này");
  }

  if (message.is_revoked) {
    const sender = await User.findOne({ user_id: message.sender_id })
      .select("name avatar")
      .lean();

    return {
      _id: message._id,
      msg_id: message.msg_id,
      conversation_id: message.conversation_id,
      sender_id: message.sender_id,
      sender_name: sender?.name || "",
      sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
      type: message.type,
      content: message.content,
      is_revoked: true,
      is_deleted: !!message.is_deleted,
      reactions: message.reactions || [],
      reply_to_msg_id: message.reply_to_msg_id,
      createdAt: message.createdAt,
      updatedAt: message.updatedAt,
    };
  }

  const wasPinned = Boolean(message.is_pinned);

  const oldContent = message.content || [];
  const msgType = message.type;

  if (['image', 'video', 'audio', 'file'].includes(msgType)) {
    const keysToDelete = Array.isArray(oldContent) ? oldContent : [oldContent];
    for (const key of keysToDelete) {
      if (!key || /^(https?:\/\/|www\.|data:)/i.test(key)) continue;
      try {
        await s3Client.send(new DeleteObjectCommand({ Bucket: bucketName, Key: key }));
      } catch(err) {
        console.error("Lỗi xóa file S3 khi thu hồi:", err);
      }
    }
  }

  message.is_revoked = true;
  message.content = [REVOKED_PLACEHOLDER];
  message.reactions = [];
  message.is_pinned = false;
  message.pinned_at = null;
  message.pinned_by = null;

  const revokedMessage = await message.save();
  const sender = await User.findOne({ user_id: revokedMessage.sender_id })
    .select("name avatar")
    .lean();
  const updatedLastMessage = await syncConversationLastMessageOnRevoke(
    conversationId,
    revokedMessage,
  );

  let systemMessage = null;
  if (wasPinned) {
    const actorName = sender?.name || "Một thành viên";
    const systemDoc = new Message({
      conversation_id: conversationId,
      sender_id: userId,
      type: "system_unpin",
      content: [`${actorName} đã gỡ ghim một tin nhắn`],
      size: 0,
    });

    const savedSystemMessage = await systemDoc.save();
    await ConversationService.updateLastMessage(
      conversationId,
      savedSystemMessage,
    );
    await messageCacheService.addMessage(conversationId, {
      ...savedSystemMessage.toObject(),
      sender_name: actorName,
    });

    systemMessage = {
      ...savedSystemMessage.toObject(),
      sender_name: actorName,
    };
  }

  await messageCacheService.updateMessage(conversationId, msgId, {
    ...revokedMessage.toObject(),
    sender_name: sender?.name || "",
    sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
  });

  return {
    _id: revokedMessage._id,
    msg_id: revokedMessage.msg_id,
    conversation_id: revokedMessage.conversation_id,
    sender_id: revokedMessage.sender_id,
    sender_name: sender?.name || "",
    sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
    type: revokedMessage.type,
    content: revokedMessage.content,
    is_revoked: true,
    is_deleted: !!revokedMessage.is_deleted,
    reactions: revokedMessage.reactions || [],
    reply_to_msg_id: revokedMessage.reply_to_msg_id,
    createdAt: revokedMessage.createdAt,
    updatedAt: revokedMessage.updatedAt,
    last_message: updatedLastMessage,
    systemMessage,
  };
};

exports.deleteMessage = async ({ conversationId, msgId, userId }) => {
  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  });

  if (!participant) {
    throw new Error("Bạn không thuộc cuộc hội thoại này");
  }

  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message || message.is_deleted) {
    throw new Error("Tin nhắn không tồn tại");
  }

  if (!Array.isArray(message.deleted_for)) {
    message.deleted_for = [];
  }

  if (!message.deleted_for.includes(userId)) {
    message.deleted_for.push(userId);
  }

  await message.save();
  await messageCacheService.updateMessage(conversationId, msgId, {
    ...message.toObject(),
  });

  return {
    _id: message._id,
    msg_id: message.msg_id,
    conversation_id: message.conversation_id,
    user_id: userId,
    delete_scope: "me",
    is_deleted_for_me: true,
  };
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

  if (!Array.isArray(message.reactions)) {
    message.reactions = [];
  }

  const currentReactionIndex = message.reactions.findIndex(
    (reaction) => reaction.user_id === userId,
  );
  const currentReaction =
    currentReactionIndex >= 0 ? message.reactions[currentReactionIndex] : null;

  if (currentReactionIndex >= 0) {
    const currentReactionType = String(currentReaction?.type || "");

    if (currentReactionType === normalizedReaction) {
      // Bấm lại cùng emoji thì bỏ reaction đó.
      message.reactions.splice(currentReactionIndex, 1);
    } else {
      // Mỗi user chỉ giữ 1 reaction, đổi sang emoji mới.
      message.reactions[currentReactionIndex] = {
        user_id: userId,
        type: normalizedReaction,
      };
    }
  } else {
    message.reactions.push({
      user_id: userId,
      type: normalizedReaction,
    });
  }

  const updatedMessage = await message.save();

  const sender = await User.findOne({ user_id: updatedMessage.sender_id })
    .select("name avatar")
    .lean();
  const cachedMessage = {
    ...updatedMessage.toObject(),
    sender_name: sender?.name || updatedMessage.sender_name || "",
    sender_avatar: sanitizeAvatarValue(
      sender?.avatar || updatedMessage.sender_avatar || "",
    ),
  };

  await messageCacheService.updateMessage(conversationId, msgId, cachedMessage);

  return {
    _id: updatedMessage._id,
    msg_id: updatedMessage.msg_id,
    conversation_id: updatedMessage.conversation_id,
    reactions: updatedMessage.reactions,
    sender_name: cachedMessage.sender_name,
    sender_avatar: cachedMessage.sender_avatar,
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

  if (isPinned && (message.is_deleted || message.is_revoked)) {
    throw new Error("Không thể ghim tin nhắn đã bị xóa hoặc thu hồi");
  }

  if (isPinned && !message.is_pinned) {
    const pinnedCount = await Message.countDocuments({
      conversation_id: conversationId,
      is_pinned: true,
    });

    if (pinnedCount >= 3) {
      throw new Error("Mỗi đoạn chat chỉ được ghim tối đa 3 tin nhắn");
    }
  }

  const wasPinned = Boolean(message.is_pinned);

  message.is_pinned = isPinned;
  message.pinned_at = isPinned ? new Date() : null;
  message.pinned_by = isPinned ? userId : null;

  const updatedMessage = await message.save();

  let systemMessage = null;
  if (wasPinned !== Boolean(isPinned)) {
    const actor = await User.findOne({ user_id: userId }).select("name").lean();
    const actorName = actor?.name || "Một thành viên";
    const systemType = isPinned ? "system_pin" : "system_unpin";
    const systemContent = [
      isPinned
        ? `${actorName} đã ghim một tin nhắn`
        : `${actorName} đã gỡ ghim một tin nhắn`,
    ];

    const systemDoc = new Message({
      conversation_id: conversationId,
      sender_id: userId,
      type: systemType,
      content: systemContent,
      size: 0,
    });

    const savedSystemMessage = await systemDoc.save();
    await ConversationService.updateLastMessage(
      conversationId,
      savedSystemMessage,
    );
    await messageCacheService.addMessage(conversationId, {
      ...savedSystemMessage.toObject(),
      sender_name: actorName,
    });

    systemMessage = {
      ...savedSystemMessage.toObject(),
      sender_name: actorName,
    };
  }

  const updatedSender = await User.findOne({
    user_id: updatedMessage.sender_id,
  })
    .select("name avatar")
    .lean();
  const senderName = updatedSender?.name || "";
  const senderAvatar = sanitizeAvatarValue(updatedSender?.avatar || "");

  const flatUpdatedMessage = {
    _id: updatedMessage._id,
    msg_id: updatedMessage.msg_id,
    conversation_id: updatedMessage.conversation_id,
    is_pinned: updatedMessage.is_pinned,
    pinned_at: updatedMessage.pinned_at,
    pinned_by: updatedMessage.pinned_by,
    type: updatedMessage.type,
    content: updatedMessage.content,
    sender_id: updatedMessage.sender_id,
    sender_name: senderName,
    sender_avatar: senderAvatar,
    createdAt: updatedMessage.createdAt,
  };

  return {
    ...flatUpdatedMessage,
    updatedMessage: flatUpdatedMessage,
    systemMessage,
  };
};

// Get pinned messages for a conversation
exports.getPinnedMessages = async (conversationId, userId) => {
  const messages = await Message.find({
    conversation_id: conversationId,
    is_pinned: true,
  })
    .sort({ pinned_at: -1 })
    .limit(3)
    .lean();

  const visibleMessages = (Array.isArray(messages) ? messages : []).filter(
    (message) => !message.is_deleted,
  );

  const senderIds = [
    ...new Set(
      visibleMessages.map((message) => String(message.sender_id || "")),
    ),
  ].filter(Boolean);

  const senders = senderIds.length
    ? await User.find({ user_id: { $in: senderIds } })
        .select("user_id name avatar")
        .lean()
    : [];

  const senderNameMap = new Map(
    senders.map((sender) => [String(sender.user_id || ""), sender]),
  );

  return visibleMessages.map((message) => ({
    ...message,
    sender_name: senderNameMap.get(String(message.sender_id || ""))?.name || "",
    sender_avatar: sanitizeAvatarValue(
      senderNameMap.get(String(message.sender_id || ""))?.avatar || "",
    ),
  }));
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

// Get media gallery items (image/video only) with pagination by media item count.
// This endpoint is independent from chat message rendering and excludes deleted/revoked messages.
exports.getMediaGallery = async (conversationId, limit = 20, skip = 0) => {
  const safeLimit = Number.isFinite(Number(limit))
    ? Math.max(1, Math.min(Number(limit), 50))
    : 20;
  const safeSkip = Number.isFinite(Number(skip))
    ? Math.max(0, Number(skip))
    : 0;

  const targetCount = safeSkip + safeLimit + 1;
  const batchSize = 50;
  let messageSkip = 0;
  let reachedEnd = false;
  const mediaPool = [];
  const senderIds = new Set();

  while (mediaPool.length < targetCount && !reachedEnd) {
    const messages = await Message.find({
      conversation_id: conversationId,
      type: { $in: ["image", "video"] },
      is_deleted: false,
      is_revoked: false,
    })
      .sort({ createdAt: -1 })
      .skip(messageSkip)
      .limit(batchSize)
      .lean();

    if (!messages.length) {
      reachedEnd = true;
      break;
    }

    messageSkip += messages.length;
    if (messages.length < batchSize) {
      reachedEnd = true;
    }

    for (const message of messages) {
      senderIds.add(String(message.sender_id || ""));
      const content = Array.isArray(message.content)
        ? message.content.filter(Boolean)
        : message.content
          ? [message.content]
          : [];

      if (message.type === "image") {
        content.forEach((rawUrl, index) => {
          mediaPool.push({
            _id: message._id,
            msg_id: message.msg_id,
            conversation_id: message.conversation_id,
            sender_id: message.sender_id,
            type: "image",
            url: String(rawUrl),
            image_index: index,
            createdAt: message.createdAt,
          });
        });
      } else if (message.type === "video" && content[0]) {
        mediaPool.push({
          _id: message._id,
          msg_id: message.msg_id,
          conversation_id: message.conversation_id,
          sender_id: message.sender_id,
          type: "video",
          url: String(content[0]),
          image_index: 0,
          createdAt: message.createdAt,
        });
      }

      if (mediaPool.length >= targetCount) {
        break;
      }
    }
  }

  const senderList = senderIds.size
    ? await User.find({ user_id: { $in: Array.from(senderIds) } })
        .select("user_id name avatar")
        .lean()
    : [];
  const senderNameMap = new Map(
    senderList.map((sender) => [
      String(sender.user_id || ""),
      sender.name || "",
    ]),
  );

  const pagedItems = mediaPool
    .slice(safeSkip, safeSkip + safeLimit)
    .map((item) => ({
      ...item,
      sender_name: senderNameMap.get(String(item.sender_id || "")) || "",
      sender_avatar: sanitizeAvatarValue(
        senderList.find(
          (sender) =>
            String(sender.user_id || "") === String(item.sender_id || ""),
        )?.avatar || "",
      ),
    }));

  const hasMore = mediaPool.length > safeSkip + safeLimit;

  return {
    items: pagedItems,
    pagination: {
      limit: safeLimit,
      skip: safeSkip,
      returned: pagedItems.length,
      hasMore,
      nextSkip: hasMore ? safeSkip + pagedItems.length : null,
    },
  };
};

// Get media messages around a target media message (_id or msg_id).
exports.getMediaAroundTarget = async (
  conversationId,
  messageId,
  before = 10,
  after = 10,
) => {
  const safeBefore = Number.isFinite(Number(before))
    ? Math.max(0, Math.min(Number(before), 50))
    : 10;
  const safeAfter = Number.isFinite(Number(after))
    ? Math.max(0, Math.min(Number(after), 50))
    : 10;

  const baseQuery = {
    conversation_id: conversationId,
    type: { $in: ["image", "video"] },
    is_deleted: false,
    is_revoked: false,
  };

  const target = await Message.findOne({
    ...baseQuery,
    $or: [{ _id: messageId }, { msg_id: String(messageId || "") }],
  }).lean();

  if (!target) {
    throw new Error("Không tìm thấy media mục tiêu");
  }

  const newer = await Message.find({
    ...baseQuery,
    createdAt: { $gt: target.createdAt },
  })
    .sort({ createdAt: 1 })
    .limit(safeAfter)
    .lean();

  const older = await Message.find({
    ...baseQuery,
    createdAt: { $lt: target.createdAt },
  })
    .sort({ createdAt: -1 })
    .limit(safeBefore)
    .lean();

  // Normalize to the same order used in gallery: newest -> oldest.
  return [...newer.reverse(), target, ...older];
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
  const urlPatternGlobal = /https?:\/\/[^\s<>"{}|\\^`[\]]+/gi;
  const urlPatternTest = /https?:\/\/[^\s<>"{}|\\^`[\]]+/i;

  const messages = await Message.find({
    conversation_id: conversationId,
    type: { $in: ["text", "link"] },
    is_deleted: false,
    is_revoked: false,
  }).sort({ createdAt: -1 });

  // Filter messages that contain links
  const messagesWithLinks = messages.filter((msg) => {
    const content = Array.isArray(msg.content)
      ? msg.content.join(" ")
      : msg.content;
    return urlPatternTest.test(String(content || ""));
  });

  // Extract links from messages
  const linksData = messagesWithLinks.slice(skip, skip + limit).map((msg) => {
    const content = Array.isArray(msg.content)
      ? msg.content.join(" ")
      : msg.content;
    const links = String(content || "").match(urlPatternGlobal) || [];

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

  const userIds = [
    ...new Set(participantsInScope.map((p) => p.user_id).filter(Boolean)),
  ];
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
      (u) =>
        queryRegex.test(String(u.name || "")) ||
        queryRegex.test(String(u.user_id || "")),
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
    deleted_for: { $ne: userId },
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
    const senderUsers = await User.find({
      user_id: { $in: senderIdsInResults },
    })
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
      sender_avatar: sanitizeAvatarValue(sender?.avatar || ""),
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
