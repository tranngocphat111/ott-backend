const { getChannel } = require("../config/rabbitmq");

const startNotificationConsumer = async (io) => {
  const channel = getChannel();
  if (!channel) {
    console.error("[NotificationConsumer] RabbitMQ channel not initialized.");
    return;
  }

  const exchange = "notification.exchange";
  const queue = "notification.realtime.queue";
  const routingKey = "notification.realtime";

  try {
    await channel.assertExchange(exchange, "topic", { durable: true });
    await channel.assertQueue(queue, { durable: true });
    await channel.bindQueue(queue, exchange, routingKey);

    console.log(`[*] Waiting for messages in ${queue}.`);

    channel.consume(
      queue,
      (msg) => {
        if (msg !== null) {
          try {
            const notification = JSON.parse(msg.content.toString());
            console.log(`[NotificationConsumer] Received realtime notification for user: ${notification.recipientId}`);

            // Emit to the specific user via Socket.io
            if (notification.recipientId) {
              io.to(`user:${notification.recipientId}`).emit("thong_bao_moi", notification);
            }

            channel.ack(msg);
          } catch (error) {
            console.error("[NotificationConsumer] Error parsing message:", error);
            channel.nack(msg, false, false);
          }
        }
      },
      { noAck: false }
    );
  } catch (error) {
    console.error("[NotificationConsumer] Setup failed:", error.message);
  }
};

module.exports = { startNotificationConsumer };
