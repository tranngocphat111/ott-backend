const amqp = require("amqplib");

const RABBITMQ_HOST = process.env.RABBITMQ_HOST || "localhost";
const RABBITMQ_PORT = process.env.RABBITMQ_PORT || "5672";
const RABBITMQ_USER = process.env.RABBITMQ_USERNAME || "admin";
const RABBITMQ_PASS = process.env.RABBITMQ_PASSWORD || "rabbit123";

const RABBITMQ_URL = process.env.RABBITMQ_URL || `amqp://${RABBITMQ_USER}:${RABBITMQ_PASS}@${RABBITMQ_HOST}:${RABBITMQ_PORT}`;

let connection = null;
let channel = null;

const connectRabbitMQ = async () => {
  try {
    if (connection && channel) return { connection, channel };

    console.log(" [ ] RabbitMQ: Connecting to", RABBITMQ_URL.replace(/:([^:@]+)@/, ":****@"));
    connection = await amqp.connect(RABBITMQ_URL);
    channel = await connection.createChannel();

    console.log(" [✓] RabbitMQ: Connected successfully");

    connection.on("error", (err) => {
      console.error("[AMQP] Connection error:", err.message);
    });

    connection.on("close", () => {
      console.warn("[AMQP] Connection closed.");
      connection = null;
      channel = null;
    });

    return { connection, channel };
  } catch (error) {
    console.error("[AMQP] Failed to connect to RabbitMQ:", error.message);
    connection = null;
    channel = null;
    throw error;
  }
};

const getChannel = () => channel;
const getConnection = () => connection;

const publishToQueue = async (queueName, payload) => {
  try {
    if (!channel) {
      await connectRabbitMQ();
    }
    // Remove assertQueue to prevent 406 PRECONDITION-FAILED if another service configured it differently.
    // The consumer service (e.g. analytics-service) should be responsible for declaring its queue with correct args.
    channel.sendToQueue(queueName, Buffer.from(JSON.stringify(payload)), {
      persistent: true,
      contentType: "application/json",
    });
  } catch (error) {
    console.error(`[AMQP] Failed to publish to queue ${queueName}:`, error.message);
    throw error;
  }
};

module.exports = { 
  connectRabbitMQ, 
  getChannel, 
  getConnection, 
  publishToQueue 
};
