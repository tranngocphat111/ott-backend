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

const initAllConsumers = async (io) => {
  try {
    const { channel } = await connectRabbitMQ();
    
    // Initialize all specific consumers here
    await initUserConsumer(channel, io);
    await initRelationshipConsumer(channel, io);
    await startNotificationConsumer(io);

    // Initialize publishers
    await initRelationshipPublisher(channel);
    await initChatPublisher(channel);
    await initChatCommandPublisher(channel);
    await initChatMessageConsumers(channel, io);
    await initChatMessageCommandConsumer(channel, io);
    await initModerationViolationConsumer(channel, io);
    
    console.log(" [✓] All RabbitMQ consumers and publishers initialized successfully");
  } catch (error) {
    console.error(" [!] Failed to initialize RabbitMQ. Retrying in 5s...", error.message);
    setTimeout(() => initAllConsumers(io), 5000);
  }
};

module.exports = { initAllConsumers };
