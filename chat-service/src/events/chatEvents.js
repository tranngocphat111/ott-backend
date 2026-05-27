const crypto = require("crypto");
const { connectRabbitMQ, getChannel } = require("../config/rabbitmq");

const CHAT_EVENTS_EXCHANGE = "chat.events";

const ROUTING_KEYS = {
  MESSAGE_CREATED: "chat.message.created.v1",
  MESSAGE_DELIVERED: "chat.message.delivered.v1",
  MESSAGE_SEEN: "chat.message.seen.v1",
  MESSAGE_STATUS_CHANGED: "chat.message.status.changed.v1",
};

const QUEUES = {
  DELIVERY: "chat.message.delivery.queue",
  RECEIPT: "chat.message.receipt.queue",
  STATUS: "chat.message.status.queue",
};

let channel = null;
let exchangeReady = false;

const randomId = () => {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return crypto.randomBytes(16).toString("hex");
};

const assertTopology = async (ch) => {
  await ch.assertExchange(CHAT_EVENTS_EXCHANGE, "topic", { durable: true });

  await ch.assertQueue(QUEUES.DELIVERY, { durable: true });
  await ch.bindQueue(
    QUEUES.DELIVERY,
    CHAT_EVENTS_EXCHANGE,
    ROUTING_KEYS.MESSAGE_CREATED,
  );

  await ch.assertQueue(QUEUES.RECEIPT, { durable: true });
  await ch.bindQueue(
    QUEUES.RECEIPT,
    CHAT_EVENTS_EXCHANGE,
    ROUTING_KEYS.MESSAGE_DELIVERED,
  );
  await ch.bindQueue(
    QUEUES.RECEIPT,
    CHAT_EVENTS_EXCHANGE,
    ROUTING_KEYS.MESSAGE_SEEN,
  );

  await ch.assertQueue(QUEUES.STATUS, { durable: true });
  await ch.bindQueue(
    QUEUES.STATUS,
    CHAT_EVENTS_EXCHANGE,
    ROUTING_KEYS.MESSAGE_STATUS_CHANGED,
  );

};

const ensureChannel = async () => {
  if (!channel) {
    channel = getChannel();
  }

  if (!channel) {
    const connected = await connectRabbitMQ();
    channel = connected.channel;
  }

  if (!exchangeReady) {
    await assertTopology(channel);
    exchangeReady = true;
  }

  return channel;
};

const initPublisher = async (ch) => {
  channel = ch;
  await assertTopology(channel);
  exchangeReady = true;
  console.log(` [✓] ChatEvents: Exchange '${CHAT_EVENTS_EXCHANGE}' asserted`);
};

const publishChatEvent = async (routingKey, payload = {}) => {
  const ch = await ensureChannel();
  const eventPayload = {
    eventId: payload.eventId || randomId(),
    emittedAt: new Date().toISOString(),
    ...payload,
  };

  ch.publish(
    CHAT_EVENTS_EXCHANGE,
    routingKey,
    Buffer.from(JSON.stringify(eventPayload)),
    {
      persistent: true,
      contentType: "application/json",
    },
  );
};

const publishMessageCreated = (payload) =>
  publishChatEvent(ROUTING_KEYS.MESSAGE_CREATED, payload);

const publishMessageDelivered = (payload) =>
  publishChatEvent(ROUTING_KEYS.MESSAGE_DELIVERED, payload);

const publishMessageSeen = (payload) =>
  publishChatEvent(ROUTING_KEYS.MESSAGE_SEEN, payload);

const publishMessageStatusChanged = (payload) =>
  publishChatEvent(ROUTING_KEYS.MESSAGE_STATUS_CHANGED, payload);

module.exports = {
  CHAT_EVENTS_EXCHANGE,
  ROUTING_KEYS,
  QUEUES,
  initPublisher,
  publishChatEvent,
  publishMessageCreated,
  publishMessageDelivered,
  publishMessageSeen,
  publishMessageStatusChanged,
};
