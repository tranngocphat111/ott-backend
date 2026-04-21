const relationshipService = require("../services/relationshipService");

const EXCHANGE_NAME = "relationship.events";
const QUEUE_NAME = "chat_service_relationship_updates";
const ROUTING_KEY = "relationship.#";

const handleRelationshipEvent = async (channel, msg) => {
  if (!msg) return;

  try {
    const content = JSON.parse(msg.content.toString());
    console.log(" [x] RelationshipConsumer: Received event:", content);

    // To avoid infinite loops, we should check if the event came from this service
    // In this case, we'll assume the publisher adds some metadata or we just handle idempotency
    await relationshipService.updateRelationshipFromEvent(content);

    channel.ack(msg);
  } catch (err) {
    console.error(" [!] RelationshipConsumer: Error processing:", err.message);
    channel.nack(msg, false, true);
  }
};

const initRelationshipConsumer = async (channel) => {
  try {
    await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });
    const q = await channel.assertQueue(QUEUE_NAME, { durable: true });
    await channel.bindQueue(q.queue, EXCHANGE_NAME, ROUTING_KEY);

    console.log(` [*] RelationshipConsumer: Listening on queue: ${q.queue}`);

    channel.consume(q.queue, (msg) => handleRelationshipEvent(channel, msg), { noAck: false });
  } catch (error) {
    console.error(" [!] RelationshipConsumer: Failed to initialize:", error.message);
    throw error;
  }
};

module.exports = { initRelationshipConsumer };
