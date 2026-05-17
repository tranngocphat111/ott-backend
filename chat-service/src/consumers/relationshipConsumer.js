const relationshipService = require("../services/relationshipService");
const ConversationService = require("../services/conversationService");
const MessageService = require("../services/messageService");
const Message = require("../models/Message");

const EXCHANGE_NAME = "relationship.events";
const ROUTING_KEY = "relationship.#";

const getUserDisplayName = async (userId) => {
  const User = require("../models/User");
  const user = await User.findOne({ user_id: userId }).select("name").lean();
  return String(user?.name || "").trim() || "Người dùng";
};

const getRelationshipSystemMessageConfig = (content, relationship) => {
  const type = String(content?.type || "").toUpperCase();
  const requesterId = relationship?.requester_id || content?.requesterId;
  const receiverId = relationship?.receiver_id || content?.receiverId;
  const relationshipId =
    content?.relationshipId || relationship?.relationship_id || relationship?._id;

  if (!requesterId || !receiverId || !relationshipId) return null;

  if (type === "REQUEST_SENT") {
    return {
      action: "friend_request_sent",
      senderId: requesterId,
      content: "Đã gửi lời mời kết bạn",
      type: "system_friend_request",
      relationshipId: String(relationshipId),
      requesterId,
      receiverId,
    };
  }

  if (type === "REQUEST_ACCEPTED") {
    return {
      action: "friend_request_accepted",
      senderId: receiverId,
      content: "Hai bạn đã trở thành bạn bè. Hãy bắt đầu trò chuyện!",
      type: "system_add",
      relationshipId: String(relationshipId),
      requesterId,
      receiverId,
    };
  }

  return null;
};

const ensureRelationshipSystemMessage = async (content, relationship, io) => {
  if (content?.source === "chat-service") return null;
  if (
    content?.relationshipId &&
    relationship?._id &&
    String(content.relationshipId) === String(relationship._id)
  ) {
    return null;
  }

  const config = getRelationshipSystemMessageConfig(content, relationship);
  if (!config) return null;
  const requesterName =
    config.action === "friend_request_sent"
      ? await getUserDisplayName(config.requesterId)
      : "";
  const messageContent =
    config.action === "friend_request_sent" && requesterName
      ? `${requesterName} đã gửi lời mời kết bạn`
      : config.content;

  const conversation = await ConversationService.findOrCreatePrivateConversation(
    config.requesterId,
    config.receiverId,
  );
  if (!conversation?._id) return null;

  const existingMessage = await Message.findOne({
    conversation_id: conversation._id,
    "system_meta.action": config.action,
    "system_meta.relationship_id": config.relationshipId,
  }).lean();

  if (existingMessage) return null;

  const message = await MessageService.sendMessage({
    conversationId: conversation._id,
    senderId: config.senderId,
    content: messageContent,
    type: config.type,
    systemMeta: {
      action: config.action,
      relationship_id: config.relationshipId,
      requester_id: config.requesterId,
      receiver_id: config.receiverId,
      ...(requesterName ? { requester_name: requesterName } : {}),
    },
  });

  if (io && message) {
    const detailedConversation = await ConversationService.getConversationById(
      conversation._id,
    );

    [config.requesterId, config.receiverId].forEach((userId) => {
      if (detailedConversation) {
        io.to(`user:${userId}`).emit("tao_phong_moi", detailedConversation);
      }
      io.to(`user:${userId}`).emit("tin_nhan", message);
    });
  }

  return message;
};

const handleRelationshipEvent = async (channel, msg, io) => {
  if (!msg) return;

  const routingKey = msg.fields.routingKey;
  const rawContent = msg.content.toString();
  
  console.log(` [x] RelationshipConsumer: Received message with routingKey: ${routingKey}`);
  console.log(` [x] RelationshipConsumer: Raw content: ${rawContent}`);

  try {
    const content = JSON.parse(rawContent);
    console.log(" [x] RelationshipConsumer: Parsed event:", content);

    // Sync database (idempotent update)
    const relationship = await relationshipService.updateRelationshipFromEvent(content);
    await ensureRelationshipSystemMessage(content, relationship, io);

    // Emit Realtime via Socket.IO to users involved
    // We use fields from the saved relationship to ensure consistent naming (requester_id, receiver_id)
    if (io && relationship) {
      console.log(` [x] RelationshipConsumer: Emitting realtime update to users: ${relationship.requester_id}, ${relationship.receiver_id}`);
      
      // Emit to each user's room
      io.to(`user:${relationship.requester_id}`).emit("cap_nhat_quan_he", relationship);
      io.to(`user:${relationship.receiver_id}`).emit("cap_nhat_quan_he", relationship);
    }

    channel.ack(msg);
  } catch (err) {
    console.error(" [!] RelationshipConsumer: Error processing:", err.message);
    // Use nack with requeue=false to avoid infinite loops on syntax errors
    channel.nack(msg, false, false);
  }
};

const initRelationshipConsumer = async (channel, io) => {
  try {
    const QUEUE_NAME = "chat_service_relationship_updates";
    await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });
    
    // Use the named queue to consume pending messages
    await channel.assertQueue(QUEUE_NAME, { durable: true });
    await channel.bindQueue(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

    console.log(` [*] RelationshipConsumer: Listening for broadcast on queue: ${QUEUE_NAME}`);

    channel.consume(QUEUE_NAME, (msg) => handleRelationshipEvent(channel, msg, io), { noAck: false });
  } catch (error) {
    console.error(" [!] RelationshipConsumer: Failed to initialize:", error.message);
    throw error;
  }
};

module.exports = { initRelationshipConsumer };
