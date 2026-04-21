const amqp = require("amqplib");

const EXCHANGE_NAME = "relationship.events";

let channel = null;

const initPublisher = async (ch) => {
  channel = ch;
  await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });
};

const publishRelationshipEvent = async (type, relationship) => {
  if (!channel) {
    console.error(" [!] RelationshipPublisher: Channel not initialized");
    return;
  }

  const routingKey = `relationship.${type.toLowerCase()}`;
  const payload = {
    type,
    relationshipId: relationship.relationship_id || relationship._id,
    requesterId: relationship.requester_id,
    receiverId: relationship.receiver_id,
    status: relationship.status,
    timestamp: new Date().toISOString(),
  };

  channel.publish(EXCHANGE_NAME, routingKey, Buffer.from(JSON.stringify(payload)), {
    persistent: true,
  });

  console.log(` [x] RelationshipPublisher: Published ${type} event to ${routingKey}`);
};

module.exports = {
  initPublisher,
  publishRelationshipEvent,
};
