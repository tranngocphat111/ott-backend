const { once } = require("events");
const { connectRabbitMQ, getChannel } = require("../config/rabbitmq");

const CHAT_COMMAND_DLX = process.env.CHAT_COMMAND_DLX || "chat.command.dlx";

const QUEUES = {
  MESSAGE_COMMAND:
    process.env.CHAT_MESSAGE_COMMAND_QUEUE ||
    "chat.message.command.queue",
  MESSAGE_COMMAND_DLQ:
    process.env.CHAT_MESSAGE_COMMAND_DLQ ||
    "chat.message.command.dlq",
};

const ROUTING_KEYS = {
  MESSAGE_COMMAND_FAILED:
    process.env.CHAT_MESSAGE_COMMAND_FAILED_ROUTING_KEY ||
    "chat.message.command.failed",
};

let publishChannel = null;
let topologyReady = false;
const waitForConfirm =
  String(process.env.CHAT_MESSAGE_COMMAND_CONFIRM || "false").toLowerCase() ===
  "true";
const waitForDrain =
  String(process.env.CHAT_MESSAGE_COMMAND_WAIT_DRAIN || "false").toLowerCase() ===
  "true";
const isPersistent =
  String(process.env.CHAT_MESSAGE_COMMAND_PERSISTENT || "true").toLowerCase() !==
  "false";

const assertTopology = async (channel) => {
  await channel.assertExchange(CHAT_COMMAND_DLX, "direct", { durable: true });
  await channel.assertQueue(QUEUES.MESSAGE_COMMAND_DLQ, { durable: true });
  await channel.bindQueue(
    QUEUES.MESSAGE_COMMAND_DLQ,
    CHAT_COMMAND_DLX,
    ROUTING_KEYS.MESSAGE_COMMAND_FAILED,
  );

  await channel.assertQueue(QUEUES.MESSAGE_COMMAND, {
    durable: true,
    arguments: {
      "x-dead-letter-exchange": CHAT_COMMAND_DLX,
      "x-dead-letter-routing-key": ROUTING_KEYS.MESSAGE_COMMAND_FAILED,
    },
  });
};

const ensurePublishChannel = async () => {
  const { connection } = await connectRabbitMQ();

  if (!publishChannel) {
    publishChannel = waitForConfirm
      ? await connection.createConfirmChannel()
      : await connection.createChannel();
    publishChannel.on("close", () => {
      publishChannel = null;
      topologyReady = false;
    });
  }

  if (!topologyReady) {
    await assertTopology(publishChannel);
    topologyReady = true;
  }

  return publishChannel;
};

const initPublisher = async (channel = getChannel()) => {
  if (!channel) return;
  await assertTopology(channel);
  topologyReady = true;
  console.log(` [✓] ChatCommandEvents: Queue '${QUEUES.MESSAGE_COMMAND}' asserted`);
};

const publishMessageCommand = async (payload = {}) => {
  const channel = await ensurePublishChannel();
  const commandId = String(payload.commandId || payload.msgId || "");
  const messagePayload = {
    commandId,
    queuedAt: new Date().toISOString(),
    ...payload,
  };

  const canContinue = channel.sendToQueue(
    QUEUES.MESSAGE_COMMAND,
    Buffer.from(JSON.stringify(messagePayload)),
    {
      persistent: isPersistent,
      contentType: "application/json",
      messageId: commandId || undefined,
      timestamp: Date.now(),
    },
  );

  if (!canContinue && waitForDrain) {
    await once(channel, "drain");
  }

  if (waitForConfirm && typeof channel.waitForConfirms === "function") {
    await channel.waitForConfirms();
  }
  return messagePayload;
};

module.exports = {
  CHAT_COMMAND_DLX,
  QUEUES,
  ROUTING_KEYS,
  assertTopology,
  initPublisher,
  publishMessageCommand,
};
