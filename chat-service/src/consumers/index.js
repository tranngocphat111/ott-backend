const { connectRabbitMQ } = require("../config/rabbitmq");
const { initUserConsumer } = require("./userConsumer");
const { initRelationshipConsumer } = require("./relationshipConsumer");
const { initPublisher: initRelationshipPublisher } = require("../events/relationshipEvents");

const initAllConsumers = async (io) => {
  try {
    const { channel } = await connectRabbitMQ();
    
    // Initialize all specific consumers here
    await initUserConsumer(channel, io);
    await initRelationshipConsumer(channel, io);

    // Initialize publishers
    await initRelationshipPublisher(channel);
    
    console.log(" [✓] All RabbitMQ consumers and publishers initialized successfully");
  } catch (error) {
    console.error(" [!] Failed to initialize RabbitMQ. Retrying in 5s...", error.message);
    setTimeout(() => initAllConsumers(io), 5000);
  }
};

module.exports = { initAllConsumers };
