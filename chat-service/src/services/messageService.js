const Message = require("../models/Message");
const Participant = require("../models/Participant");
const Conversation = require("../models/Conversation");
const User = require("../models/User");
const Relationship = require("../models/Relationship");
const ConversationService = require("./conversationService");
const messageCacheService = require("./messageCacheService");
const {
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
  CopyObjectCommand,
  GetObjectTaggingCommand,
  HeadObjectCommand,
} = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
const { s3Client, bucketName } = require("../config/s3");
const { publishMessageSentEvent } = require("./analyticsPublisher");
const {
  publishMessageForReview,
  publishMessageImageForReview,
} = require("./chatModerationPublisher");
const { publishNotification } = require("../events/notificationEvents");

const fs = require("fs/promises");
const path = require("path");
const os = require("os");
const { spawn } = require("child_process");
const { pipeline } = require("stream/promises");
const crypto = require("crypto");

const REVOKED_PLACEHOLDER = "Tin nhắn đã được thu hồi";
const S3_CACHE_CONTROL = "public, max-age=31536000, immutable";
const PUSH_NOTIFICATION_MESSAGE_TYPES = new Set([
  "text",
  "image",
  "video",
  "audio",
  "file",
  "link",
  "poll",
]);
const S3_POLICY_SIGNAL_PATTERN =
  /(violation|moderation|unsafe|malware|virus|infected|blocked|denied|explicit.?deny|quarantine|sensitive|nsfw|adult|explicit)/i;
const S3_POLICY_CLEAN_VALUE_PATTERN =
  /^(clean|ok|pass|passed|safe|allowed|false|0|no|none)$/i;
const MEDIA_MESSAGE_TYPES = new Set(["image", "video", "file", "audio"]);
const sanitizeS3FileName = (fileName) => {
  const baseName =
    String(fileName || "file")
      .split(/[\\/]/)
      .pop()
      ?.normalize("NFKD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^a-zA-Z0-9._-]/g, "_")
      .replace(/_+/g, "_")
      .replace(/^_+|_+$/g, "") || "file";

  return baseName.slice(0, 160) || "file";
};

const hasPersistableMeta = (value) => {
  if (value == null) return false;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === "object") {
    if (Object.prototype.toString.call(value) !== "[object Object]") {
      return true;
    }
    return Object.keys(value).length > 0;
  }
  return true;
};

const normalizePollOptionsForStorage = (pollOptions) => {
  if (!Array.isArray(pollOptions)) return [];

  return pollOptions
    .map((option) => {
      const normalizedOption = {
        id: String(option?.id || "").trim(),
        name: String(option?.name || "").trim(),
      };

      const voters = Array.isArray(option?.voters)
        ? option.voters.filter(Boolean).map((voterId) => String(voterId))
        : [];

      if (voters.length > 0) {
        normalizedOption.voters = voters;
      }

      return normalizedOption;
    })
    .filter((option) => option.id && option.name);
};

const buildMessageCreatePayload = ({
  conversationId,
  senderId,
  content,
  type,
  size,
  replyToMsgId,
  pollQuestion,
  pollMultipleChoice,
  pollOptions,
  systemMeta,
}) => {
  const messageType = type || "text";
  const payload = {
    conversation_id: conversationId,
    sender_id: senderId,
    content,
    type: messageType,
  };

  if (replyToMsgId) {
    payload.reply_to_msg_id = replyToMsgId;
  }

  const numericSize = Number(size);
  if (
    MEDIA_MESSAGE_TYPES.has(messageType) &&
    Number.isFinite(numericSize) &&
    numericSize > 0
  ) {
    payload.size = numericSize;
  }

  if (messageType === "poll") {
    const normalizedQuestion = String(pollQuestion || "").trim();
    if (normalizedQuestion) {
      payload.poll_question = normalizedQuestion;
    }

    if (pollMultipleChoice === true) {
      payload.poll_multiple_choice = true;
    }

    const normalizedOptions = normalizePollOptionsForStorage(pollOptions);
    if (normalizedOptions.length > 0) {
      payload.poll_options = normalizedOptions;
    }
  }

  if (hasPersistableMeta(systemMeta)) {
    payload.system_meta = systemMeta;
  }

  return payload;
};

const isParticipantNotificationEnabled = (participant) => {
  const settings = participant?.settings || {};
  const status = settings.notification_status || "on";

  if (status === "off") return false;
  if (status !== "mute") return true;

  if (!settings.mute_until) return false;
  const muteUntil = new Date(settings.mute_until).getTime();
  return Number.isNaN(muteUntil) || muteUntil <= Date.now();
};

const resolvePublicMediaUrl = (value) => {
  const raw = String(value || "").trim();
  if (!raw || /^data:image\//i.test(raw)) return "";
  if (/^https?:\/\//i.test(raw)) return raw;
  if (!bucketName) return "";

  const region = process.env.AWS_REGION || "ap-southeast-1";
  const key = raw
    .replace(/^\/+/, "")
    .split("/")
    .filter(Boolean)
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  return key ? `https://${bucketName}.s3.${region}.amazonaws.com/${key}` : "";
};

const getMessageNotificationPayload = ({ message, senderName, conversation }) => {
  const senderLabel = senderName || "Ai đó";
  const groupName = conversation?.type === "group" ? String(conversation?.name || "").trim() : "";
  const content = Array.isArray(message.content)
    ? message.content.filter(Boolean)
    : [message.content].filter(Boolean);
  const firstContent = String(content[0] || "");
  const title = groupName ? `${senderLabel} trong ${groupName}` : senderLabel;

  switch (message.type) {
    case "image":
      return {
        title,
        body: `Đã gửi ${content.length > 1 ? `${content.length} hình ảnh` : "một hình ảnh"}`,
      };
    case "video":
      return { title, body: "Đã gửi một video" };
    case "audio":
      return { title, body: "Đã gửi một tin nhắn thoại" };
    case "file":
      return { title, body: "Đã gửi một tệp" };
    case "poll":
      return { title, body: "Đã tạo một cuộc bình chọn" };
    default: {
      const preview = firstContent.length > 90 ? `${firstContent.slice(0, 90)}...` : firstContent;
      return {
        title,
        body: preview || "Tin nhắn mới",
      };
    }
  }
};

const publishMessageNotificationsBestEffort = async ({
  conversationId,
  senderId,
  message,
  senderName,
  conversation,
}) => {
  if (!PUSH_NOTIFICATION_MESSAGE_TYPES.has(String(message?.type || ""))) return;

  try {
    const recipients = await Participant.find({
      conversation_id: conversationId,
      user_id: { $ne: senderId },
      status: "joined",
      "settings.removed_from_group_at": null,
    })
      .select("user_id settings")
      .lean();

    const notificationPayload = getMessageNotificationPayload({
      message,
      senderName,
      conversation,
    });
    const senderAvatarUrl = resolvePublicMediaUrl(message.sender_avatar);
    const content = `${notificationPayload.title}: ${notificationPayload.body}`;

    await Promise.allSettled(
      recipients
        .filter(isParticipantNotificationEnabled)
        .map((recipient) =>
          publishNotification({
            recipientId: recipient.user_id,
            senderId,
            type: "CHAT_MESSAGE",
            content,
            title: notificationPayload.title,
            body: notificationPayload.body,
            imageUrl: senderAvatarUrl,
            referenceId: String(conversationId),
            pushOnly: true,
          }),
        ),
    );
  } catch (error) {
    console.warn(
      "[notification] publish chat message notification failed:",
      error?.message || error,
    );
  }
};

const resolveS3ContentDisposition = (fileCategory, fileName) => {
  const disposition = ["image", "video", "audio"].includes(fileCategory)
    ? "inline"
    : "attachment";
  return `${disposition}; filename="${sanitizeS3FileName(fileName)}"`;
};

const extractS3ObjectKey = (value) => {
  const raw = String(value || "").trim();
  if (!raw) return "";

  try {
    if (/^https?:\/\//i.test(raw)) {
      const url = new URL(raw);
      return decodeURIComponent(url.pathname.replace(/^\/+/, ""));
    }
  } catch {
    // Fall through to raw key normalization.
  }

  return decodeURIComponent(raw.replace(/^\/+/, "").split("?")[0] || "");
};

const isPolicySignal = (key, value) => {
  const normalizedValue = String(value ?? "").trim();
  if (S3_POLICY_CLEAN_VALUE_PATTERN.test(normalizedValue)) return false;

  return (
    S3_POLICY_SIGNAL_PATTERN.test(String(key || "")) ||
    S3_POLICY_SIGNAL_PATTERN.test(normalizedValue)
  );
};

const getS3MediaWarning = async (rawKey, index) => {
  const key = extractS3ObjectKey(rawKey);
  if (!key) return null;

  try {
    const [headResult, tagResult] = await Promise.allSettled([
      s3Client.send(new HeadObjectCommand({ Bucket: bucketName, Key: key })),
      s3Client.send(new GetObjectTaggingCommand({ Bucket: bucketName, Key: key })),
    ]);

    const metadata =
      headResult.status === "fulfilled" ? headResult.value.Metadata || {} : {};
    const tags =
      tagResult.status === "fulfilled" ? tagResult.value.TagSet || [] : [];

    const metadataSignal = Object.entries(metadata).find(([name, value]) =>
      isPolicySignal(name, value),
    );
    const tagSignal = tags.find((tag) => isPolicySignal(tag.Key, tag.Value));
    const signal = tagSignal || metadataSignal;

    if (!signal) return null;

    const signalName = Array.isArray(signal) ? signal[0] : signal.Key;
    const signalValue = Array.isArray(signal) ? signal[1] : signal.Value;

    return {
      index,
      key,
      source: "s3",
      reason: String(signalValue || signalName || "policy_violation"),
    };
  } catch (error) {
    console.warn(
      "Không thể đọc metadata/tag S3 để kiểm tra media:",
      error?.message || error,
    );
    return null;
  }
};

/*
      "Không thể kiểm tra Rekognition moderation:",
      error?.message || error,
    );

    if (!MODERATION_FAIL_CLOSED) return null;

    return buildModerationWarning({
      index,
      key,
      source: "rekognition_error",
      reason: "moderation_unavailable",
    });
  }
};

*/
const buildMediaPolicyMeta = async (type, contentArray) => {
  if (!["image", "video"].includes(type)) return null;

  const warnings = (
    await Promise.all(
      contentArray.map(async (key, index) => {
        const s3Warning = await getS3MediaWarning(key, index);
        if (s3Warning) return s3Warning;

        return null;
      }),
    )
  ).filter(Boolean);

  if (!warnings.length) return null;

  return {
    media_policy_status: "flagged",
    media_warnings: warnings,
  };
};

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

const normalizeMessageTypes = (types) => {
  if (!types) return [];
  const rawTypes = types instanceof Set ? Array.from(types) : types;
  return (Array.isArray(rawTypes) ? rawTypes : [rawTypes])
    .map((type) => String(type || "").trim())
    .filter(Boolean);
};

const isMessageAfterDeletedMarker = (message, deletedMsgId = "0") => {
  const marker = String(deletedMsgId || "0");
  if (!marker || marker === "0") return true;

  const messageId = String(message?.msg_id || "");
  if (!messageId) return false;

  try {
    return BigInt(messageId) > BigInt(marker);
  } catch {
    return messageId > marker;
  }
};

const getUserMessageVisibilityScope = async (conversationId, userId) => {
  if (!userId) {
    return { canRead: true, deletedMsgId: "0" };
  }

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: userId,
  })
    .select("deleted_msg_id status")
    .lean();

  if (!participant || participant.status !== "joined") {
    return { canRead: false, deletedMsgId: "0" };
  }

  return {
    canRead: true,
    deletedMsgId: String(participant.deleted_msg_id || "0"),
  };
};

exports.getMessages = async (
  conversationId,
  { limit = 20, skip = 0, types, userId } = {},
) => {
  const messageTypes = normalizeMessageTypes(types);
  const { canRead, deletedMsgId } = await getUserMessageVisibilityScope(
    conversationId,
    userId,
  );

  if (!canRead) return [];

  const query = {
    conversation_id: conversationId,
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
    ...(userId ? { deleted_for: { $ne: userId } } : {}),
    ...(deletedMsgId !== "0" ? { msg_id: { $gt: deletedMsgId } } : {}),
    ...(messageTypes.length ? { type: { $in: messageTypes } } : {}),
  };

  const messages = await Message.find(query)
    .sort({ msg_id: -1 })
    .skip(skip)
    .limit(limit)
    .lean();
  return messages.filter(
    (message) =>
      !message.deleted_at &&
      isMessageAfterDeletedMarker(message, deletedMsgId),
  );
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
    poll_question: message.type === "poll" ? message.poll_question : undefined,
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
  if (!fileName || !fileType) {
    throw new Error("Thiếu tên file hoặc loại file");
  }

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

  const safeFileName = sanitizeS3FileName(fileName);
  const uniqueId = crypto.randomBytes(8).toString("hex");

  const key = `messages/${fileCategory}/${year}/${month}/${day}/${uniqueId}_${safeFileName}`;

  const command = new PutObjectCommand({
    Bucket: bucketName,
    Key: key,
    ContentType: fileType,
    CacheControl: S3_CACHE_CONTROL,
    ContentDisposition: resolveS3ContentDisposition(fileCategory, safeFileName),
  });

  const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 300 });
  const fileUrl = `https://${bucketName}.s3.${process.env.AWS_REGION || "ap-southeast-1"}.amazonaws.com/${key}`;

  console.log("Generated Presigned URL for:", fileName);
  console.log("Final response object:", { uploadUrl: "...", fileCategory, key, fileUrl });

  return { uploadUrl, fileCategory, key, fileUrl };
};

exports.sendMessage = async ({
  conversationId,
  senderId,
  content,
  type,
  size,
  replyToMsgId,
  pollQuestion,
  pollMultipleChoice,
  pollOptions,
  systemMeta,
}) => {
  // Nếu content đã là array (image keys) thì dùng trực tiếp, không thì wrap
  const contentArray = Array.isArray(content) ? content : [content];
  const normalizedContent = [...contentArray];

  if (type === "audio" && normalizedContent.length > 0) {
    normalizedContent[0] = await maybeTranscodeVoiceKey(
      String(normalizedContent[0] || ""),
    );
  }

  const mediaPolicyMeta =
    type === "image" ? null : await buildMediaPolicyMeta(type, normalizedContent);

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

  const participant = await Participant.findOne({
    conversation_id: conversationId,
    user_id: senderId,
  }).lean();

  if (!participant) {
    throw new Error("Bạn không thuộc cuộc hội thoại này");
  }

  // Check for blocking in private conversations
  const conversation = await Conversation.findById(conversationId).lean();
  if (conversation && conversation.type === "private") {
    const otherParticipant = await Participant.findOne({
      conversation_id: conversationId,
      user_id: { $ne: senderId },
    }).lean();

    if (otherParticipant) {
      const relationship = await Relationship.findOne({
        $or: [
          { requester_id: senderId, receiver_id: otherParticipant.user_id },
          { requester_id: otherParticipant.user_id, receiver_id: senderId },
        ],
      }).lean();

      if (relationship && relationship.status === "BLOCKED") {
        if (relationship.requester_id === senderId) {
          throw new Error("Bạn đã chặn người này. Hãy bỏ chặn để tiếp tục trò chuyện.");
        } else {
          throw new Error("Bạn đã bị người này chặn.");
        }
      }
    }
  }

  if (participant?.settings?.removed_from_group_at) {
    throw new Error("Bạn đã bị đuổi khỏi nhóm");
  }

  if (participant?.settings?.group_dissolved_at) {
    throw new Error("Nhóm đã được giải tán");
  }

  const newMessage = new Message(
    buildMessageCreatePayload({
      conversationId,
      senderId,
      content: normalizedContent,
      type,
      size,
      replyToMsgId,
      pollQuestion,
      pollMultipleChoice,
      pollOptions,
      systemMeta: systemMeta !== undefined ? systemMeta : mediaPolicyMeta,
    }),
  );

  const savedMessage = await newMessage.save();

  if (type === "text") {
    publishMessageForReview(
      savedMessage.msg_id,
      senderId,
      normalizedContent[0],
      conversationId,
    );
  }

  if (type === "image") {
    normalizedContent.forEach((objectKey, imageIndex) => {
      publishMessageImageForReview({
        messageId: savedMessage.msg_id,
        senderId,
        objectKey,
        imageIndex,
        conversationId,
        bucketName,
      });
    });
  }

  const updatedConversation = await ConversationService.updateLastMessage(
    conversationId,
    savedMessage,
  );

  try {
    await publishMessageSentEvent({
      messageId: savedMessage.msg_id,
      userId: senderId,
      messageType: type,
    });
  } catch (error) {
    // Do not block chat delivery when analytics pipeline is unavailable
    console.warn(
      "[analytics] publish message.sent failed:",
      error?.message || error,
    );
  }

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
  publishMessageNotificationsBestEffort({
    conversationId,
    senderId,
    message: enrichedMessage,
    senderName: enrichedMessage.sender_name,
    conversation,
  }).catch((error) => {
    console.warn(
      "[notification] async chat message notification failed:",
      error?.message || error,
    );
  });

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
  // If user is invited but not yet joined, they shouldn't see messages
  if (userId) {
    const participant = await Participant.findOne({
      conversation_id: conversationId,
      user_id: userId,
    }).lean();
    
    if (participant && participant.status === "invited") {
      return []; // Return empty history for invited users
    }
  }

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
    reactions: Array.isArray(updatedMessage.reactions)
      ? updatedMessage.reactions
      : [],
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
    reactions: Array.isArray(updatedMessage.reactions)
      ? updatedMessage.reactions
      : [],
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
    is_pinned: Boolean(updatedMessage.is_pinned),
    pinned_at: updatedMessage.pinned_at || null,
    pinned_by: updatedMessage.pinned_by || null,
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
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
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
      is_deleted: { $ne: true },
      is_revoked: { $ne: true },
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
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
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
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
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
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
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
  scope = null,
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

  // Global phone search integration
  const isPhone = /^\d{10,11}$/.test(query);
  if (isPhone && !contacts.some(c => c.phone === query)) {
    const globalUser = await User.findOne({
      $or: [{ phone: query }, { user_id: query }]
    }).select("user_id name avatar phone").lean();
    
    if (globalUser && globalUser.user_id !== userId) {
      contacts.unshift({
        user_id: globalUser.user_id,
        name: globalUser.name || globalUser.phone || globalUser.user_id,
        avatar: globalUser.avatar || "",
        phone: globalUser.phone || globalUser.user_id,
        conversation_ids: [],
      });
    }
  }

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
    is_deleted: { $ne: true },
    is_revoked: { $ne: true },
    deleted_for: { $ne: userId },
  };

  if (senderId) {
    messageFilter.sender_id = senderId;
  }

  const shouldSearchMessages = !scope || scope.includes("messages") || scope.includes("all");
  const shouldSearchFiles = !scope || scope.includes("files") || scope.includes("all");
  const shouldSearchMedia = !scope || scope.includes("media") || scope.includes("all");

  const [matchedMessages, matchedFiles, matchedMedia] = await Promise.all([
    shouldSearchMessages
      ? Message.find({
          ...messageFilter,
          type: { $in: ["text", "link"] },
          content: { $elemMatch: { $regex: queryRegex } },
        })
          .sort({ createdAt: -1 })
          .limit(safeLimit)
          .lean()
      : Promise.resolve([]),
    shouldSearchFiles
      ? Message.find({
          ...messageFilter,
          type: "file",
          content: { $elemMatch: { $regex: queryRegex } },
        })
          .sort({ createdAt: -1 })
          .limit(safeLimit)
          .lean()
      : Promise.resolve([]),
    shouldSearchMedia
      ? Message.find({
          ...messageFilter,
          type: { $in: ["image", "video"] },
          content: { $elemMatch: { $regex: queryRegex } },
        })
          .sort({ createdAt: -1 })
          .limit(safeLimit)
          .lean()
      : Promise.resolve([]),
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

// Vote for a poll
exports.votePoll = async ({ conversationId, msgId, userId, optionIds }) => {
  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message) {
    throw new Error("Tin nhắn không tồn tại");
  }

  if (message.type !== "poll") {
    throw new Error("Tin nhắn không phải là khảo sát");
  }

  if (message.is_deleted || message.is_revoked) {
    throw new Error("Khảo sát đã bị xóa hoặc thu hồi");
  }

  if (message.poll_locked) {
    throw new Error("Khảo sát đã bị khóa");
  }

  const voter = await User.findOne({ user_id: userId }).select("name").lean();
  const voterName = voter?.name || "Một thành viên";
  const pollOptions = Array.isArray(message.poll_options)
    ? message.poll_options
    : [];

  // Check if they were already in any option
  const wasVoted = pollOptions.some((opt) =>
    (Array.isArray(opt.voters) ? opt.voters : []).some(
      (v) => String(v) === String(userId),
    ),
  );

  // Remove user from all options first
  pollOptions.forEach((opt) => {
    opt.voters = (Array.isArray(opt.voters) ? opt.voters : []).filter(
      (voterId) => String(voterId) !== String(userId),
    );
  });

  // Then add user to selected options
  const selectedOptionNames = [];
  if (Array.isArray(optionIds) && optionIds.length > 0) {
    if (!message.poll_multiple_choice && optionIds.length > 1) {
      throw new Error("Khảo sát này chỉ cho phép chọn 1 đáp án");
    }

    pollOptions.forEach((opt) => {
      if (optionIds.includes(String(opt.id))) {
        if (!Array.isArray(opt.voters)) {
          opt.voters = [];
        }
        opt.voters.push(userId);
        selectedOptionNames.push(opt.name);
      }
    });
  }

  const updatedMessage = await message.save();

  // Create system notification
  let systemMessage = null;
  if (selectedOptionNames.length > 0) {
    const actionText = wasVoted 
      ? `${voterName} đã thay đổi bình chọn` 
      : `${voterName} đã bình chọn cho "${selectedOptionNames.join(", ")}"`;

    const systemDoc = new Message({
      conversation_id: conversationId,
      sender_id: userId,
      type: "system_poll",
      content: [actionText],
    });

    const savedSystemMessage = await systemDoc.save();
    
    // Enrich system message for broadcast
    systemMessage = {
      ...savedSystemMessage.toObject(),
      sender_name: voterName,
    };

    // Update conversation last message and cache
    await ConversationService.updateLastMessage(conversationId, savedSystemMessage);
    await messageCacheService.addMessage(conversationId, systemMessage);
  }

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
    ...cachedMessage,
    systemMessage,
  };
};

// Lock a poll so nobody can vote or change their vote anymore
exports.lockPoll = async ({ conversationId, msgId, userId }) => {
  const message = await Message.findOne({
    msg_id: msgId,
    conversation_id: conversationId,
  });

  if (!message) {
    throw new Error("Tin nhắn không tồn tại");
  }

  if (message.type !== "poll") {
    throw new Error("Tin nhắn không phải là khảo sát");
  }

  if (message.is_deleted || message.is_revoked) {
    throw new Error("Khảo sát đã bị xóa hoặc thu hồi");
  }

  if (String(message.sender_id) !== String(userId)) {
    throw new Error("Chỉ người tạo khảo sát mới có thể khóa bình chọn");
  }

  if (message.poll_locked) {
    throw new Error("Khảo sát đã bị khóa");
  }

  message.poll_locked = true;
  message.poll_locked_at = new Date();
  message.poll_locked_by = userId;

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

  return cachedMessage;
};
