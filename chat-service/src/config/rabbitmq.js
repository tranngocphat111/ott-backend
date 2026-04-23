const amqp = require("amqplib");

const RABBITMQ_URL = process.env.RABBITMQ_URL || "amqp://localhost:5672";

let connectionPromise = null;
let channelPromise = null;

async function getChannel() {
  if (!connectionPromise) {
    connectionPromise = amqp.connect(RABBITMQ_URL).catch((error) => {
      connectionPromise = null;
      throw error;
    });
  }

  if (!channelPromise) {
    channelPromise = connectionPromise
      .then((conn) => conn.createChannel())
      .catch((error) => {
        channelPromise = null;
        throw error;
      });
  }

  return channelPromise;
}

async function publishToQueue(queueName, payload) {
  const channel = await getChannel();
  await channel.assertQueue(queueName, { durable: true });

  channel.sendToQueue(queueName, Buffer.from(JSON.stringify(payload)), {
    persistent: true,
    contentType: "application/json",
  });
}

module.exports = {
  publishToQueue,
};
const RABBITMQ_HOST = process.env.RABBITMQ_HOST || "localhost";
const RABBITMQ_PORT = process.env.RABBITMQ_PORT || "5672";
const RABBITMQ_USER = process.env.RABBITMQ_USERNAME || "admin";
const RABBITMQ_PASS = process.env.RABBITMQ_PASSWORD || "rabbit123";

const RABBITMQ_URL = process.env.RABBITMQ_URL || `amqp://${RABBITMQ_USER}:${RABBITMQ_PASS}@${RABBITMQ_HOST}:${RABBITMQ_PORT}`;

let connection = null;
let channel = null;

const connectRabbitMQ = async () => {
  try {
    if (connection) return { connection, channel };

    connection = await amqp.connect(RABBITMQ_URL);
    channel = await connection.createChannel();

    console.log(" [✓] RabbitMQ: Connected successfully");

    connection.on("error", (err) => {
      console.error("[AMQP] Connection error:", err.message);
    });

    connection.on("close", () => {
      console.warn("[AMQP] Connection closed. Restarting process might be needed or handled by consumers.");
      connection = null;
      channel = null;
    });

    return { connection, channel };
  } catch (error) {
    console.error("[AMQP] Failed to connect to RabbitMQ:", error.message);
    throw error;
  }
};

const getChannel = () => channel;
const getConnection = () => connection;

module.exports = { connectRabbitMQ, getChannel, getConnection };
