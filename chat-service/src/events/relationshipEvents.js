const { getChannel } = require("../config/rabbitmq");

const EXCHANGE_NAME = "relationship.events";

let channel = null;

const initPublisher = async (ch) => {
  channel = ch;
  try {
    await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });
    console.log(` [✓] RelationshipPublisher: Exchange '${EXCHANGE_NAME}' asserted`);
  } catch (error) {
    console.error(` [!] RelationshipPublisher: Failed to assert exchange: ${error.message}`);
  }
};

const publishRelationshipEvent = async (type, relationship, metadata = {}) => {
  // Use initialized channel or fallback to central one
  const ch = channel || getChannel();

  if (!ch) {
    console.error(" [!] RelationshipPublisher: Channel not initialized and no central channel available");
    return;
  }

  try {
    const routingKey = `relationship.${type.toLowerCase()}`;
    const payload = {
      type,
      source: "chat-service",
      relationshipId: relationship._id || relationship.relationship_id,
      requesterId: relationship.requester_id,
      receiverId: relationship.receiver_id,
      status: relationship.status,
      ...metadata,
      timestamp: new Date().toISOString(),
    };

    ch.publish(EXCHANGE_NAME, routingKey, Buffer.from(JSON.stringify(payload)), {
      persistent: true,
    });

    console.log(` [x] RelationshipPublisher: Published ${type} event to ${routingKey}`);
  } catch (error) {
    console.error(` [!] RelationshipPublisher: Error publishing ${type} event: ${error.message}`);
    throw error;
  }
};

module.exports = {
  initPublisher,
  publishRelationshipEvent,
};
