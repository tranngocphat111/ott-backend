const relationshipService = require("../services/relationshipService");

const EXCHANGE_NAME = "relationship.events";
const ROUTING_KEY = "relationship.#";

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
