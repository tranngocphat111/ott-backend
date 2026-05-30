const { connectRabbitMQ } = require("../config/rabbitmq");
const { initUserConsumer } = require("./userConsumer");
const { initRelationshipConsumer } = require("./relationshipConsumer");
const { initPublisher: initRelationshipPublisher } = require("../events/relationshipEvents");
const { initPublisher: initChatPublisher } = require("../events/chatEvents");
const { initPublisher: initChatCommandPublisher } = require("../events/chatCommandEvents");
const { initChatMessageConsumers } = require("./chatMessageConsumer");
const { initChatMessageCommandConsumer } = require("./chatMessageCommandConsumer");
const { startNotificationConsumer } = require("./notificationConsumer");
const { initModerationViolationConsumer } = require("./moderationViolationConsumer");

const envEnabled = (name, defaultValue = true) => {
  const raw = process.env[name];
  if (raw === undefined) return defaultValue;
  return String(raw).toLowerCase() !== "false";
};

const initAllConsumers = async (io) => {
  try {
    const { channel } = await connectRabbitMQ();
    const userConsumersEnabled = envEnabled("CHAT_USER_CONSUMERS_ENABLED", true);
    const relationshipConsumersEnabled = envEnabled("CHAT_RELATIONSHIP_CONSUMERS_ENABLED", true);
    const notificationConsumerEnabled = envEnabled("CHAT_NOTIFICATION_CONSUMER_ENABLED", true);
    const realtimeConsumersEnabled = envEnabled("CHAT_REALTIME_CONSUMERS_ENABLED", true);
    const commandConsumerEnabled = envEnabled("CHAT_MESSAGE_COMMAND_CONSUMER_ENABLED", true);
    const moderationConsumerEnabled = envEnabled("CHAT_MODERATION_CONSUMER_ENABLED", true);
    
    // Initialize all specific consumers here
    if (userConsumersEnabled) {
      await initUserConsumer(channel, io);
    }
    if (relationshipConsumersEnabled) {
      await initRelationshipConsumer(channel, io);
    }
    if (notificationConsumerEnabled) {
      await startNotificationConsumer(io);
    }

    // Initialize publishers
    await initRelationshipPublisher(channel);
    await initChatPublisher(channel);
    await initChatCommandPublisher(channel);
    if (realtimeConsumersEnabled) {
      await initChatMessageConsumers(channel, io);
    }
    if (commandConsumerEnabled) {
      await initChatMessageCommandConsumer(channel, io);
    }
    if (moderationConsumerEnabled) {
      await initModerationViolationConsumer(channel, io);
    }
    
    console.log(" [✓] All RabbitMQ consumers and publishers initialized successfully");
  } catch (error) {
    console.error(" [!] Failed to initialize RabbitMQ. Retrying in 5s...", error.message);
    setTimeout(() => initAllConsumers(io), 5000);
  }
};

module.exports = { initAllConsumers };
