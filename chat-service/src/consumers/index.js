const { connectRabbitMQ } = require("../config/rabbitmq");
const { initUserConsumer } = require("./userConsumer");
const { initRelationshipConsumer } = require("./relationshipConsumer");
const { initPublisher: initRelationshipPublisher } = require("../events/relationshipEvents");

const initAllConsumers = async () => {
  try {
    const { channel } = await connectRabbitMQ();
    
    // Initialize all specific consumers here
    await initUserConsumer(channel);
    await initRelationshipConsumer(channel);

    // Initialize publishers
    await initRelationshipPublisher(channel);
    
    console.log(" [✓] All RabbitMQ consumers and publishers initialized successfully");
  } catch (error) {
    console.error(" [!] Failed to initialize RabbitMQ. Retrying in 5s...", error.message);
    setTimeout(initAllConsumers, 5000);
  }
};

module.exports = { initAllConsumers };
