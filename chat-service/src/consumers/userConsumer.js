const User = require("../models/User");
const userService = require("../services/userService");

const EXCHANGE_NAME = "user.events";
const QUEUE_NAME = "chat_service_user_created";
const ROUTING_KEY = "user.created";

const handleUserCreated = async (channel, msg) => {
  if (!msg) return;

  try {
    const content = JSON.parse(msg.content.toString());
    console.log(" [x] UserConsumer: Received user.created event:", content);

    const { userId } = content;

    if (!userId) {
      console.warn(" [!] UserConsumer: Skipping event due to missing userId");
      return channel.ack(msg);
    }

    // Call userService to handle creation logic
    await userService.createUser(content);
    console.log(` [v] UserConsumer: Processed user creation for ${userId}`);

    channel.ack(msg);
  } catch (err) {
    console.error(" [!] UserConsumer: Error processing message:", err.message);
    
    // Nack and requeue to retry later if it's a temporary issue (like DB down)
    // If it's a permanent error (poison pill), in production we'd use a Dead Letter Queue (DLQ)
    // For now, we follow the user's request to keep it on the queue.
    channel.nack(msg, false, true);
  }
};

const initUserConsumer = async (channel) => {
  try {
    // Assert exchange
    await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });
    
    // Assert queue
    const q = await channel.assertQueue(QUEUE_NAME, { durable: true });

    // Bind queue
    await channel.bindQueue(q.queue, EXCHANGE_NAME, ROUTING_KEY);

    console.log(` [*] UserConsumer: Listening for events on queue: ${q.queue}`);

    channel.consume(q.queue, (msg) => handleUserCreated(channel, msg), { noAck: false });
  } catch (error) {
    console.error(" [!] UserConsumer: Failed to initialize:", error.message);
    throw error;
  }
};

module.exports = { initUserConsumer };
