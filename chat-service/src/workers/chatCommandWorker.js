const connectDB = require("../config/db");
const { connectRabbitMQ } = require("../config/rabbitmq");
const { initPublisher: initChatPublisher } = require("../events/chatEvents");
const {
  initPublisher: initChatCommandPublisher,
} = require("../events/chatCommandEvents");
const {
  initChatMessageCommandConsumer,
} = require("../consumers/chatMessageCommandConsumer");

const start = async () => {
  await connectDB();
  const { channel } = await connectRabbitMQ();

  await initChatPublisher(channel);
  await initChatCommandPublisher(channel);
  await initChatMessageCommandConsumer(channel, null);

  console.log(" [✓] Chat command worker started");
};

start().catch((error) => {
  console.error(" [!] Chat command worker failed to start:", error);
  process.exit(1);
});
