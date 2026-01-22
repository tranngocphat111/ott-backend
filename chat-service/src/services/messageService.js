const Message = require("../models/Message");
const ConversationService = require("./conversationService");
const { PutObjectCommand } = require("@aws-sdk/client-s3");
const { getSignedUrl } = require("@aws-sdk/s3-request-presigner");
const { s3Client, bucketName } = require("../config/s3");

const crypto = require("crypto"); 

exports.generatePresignedUrl = async (fileName, fileType) => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");

  const fileCategory = fileType.startsWith("image/")
    ? "image"
    : fileType.startsWith("video/")
      ? "video"
      : "file";

  const uniqueId = crypto.randomBytes(8).toString("hex");

  const key = `messages/${fileCategory}/${year}/${month}/${day}/${uniqueId}_${fileName}`;

  const command = new PutObjectCommand({
    Bucket: bucketName,
    Key: key,
    ContentType: fileType,
  });

  const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 300 });

  const fileUrl = `https://${bucketName}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;

  return { uploadUrl, fileUrl, key };
};

exports.sendMessage = async ({ conversationId, senderId, content, type, size }) => {
  const newMessage = new Message({
    conversation_id: conversationId,
    sender_id: senderId,
    content: [content],
    type: type,
    size: size,
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
