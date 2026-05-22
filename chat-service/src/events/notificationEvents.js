const { publishToQueue } = require("../config/rabbitmq");

const publishNotification = async ({ recipientId, senderId, type, content, referenceId, pushOnly = false }) => {
  try {
    const payload = {
      recipientId,
      senderId,
      type,
      content,
      referenceId,
      pushOnly,
    };
    await publishToQueue("notification.inapp.queue", payload);
    console.log(`[Notification] Published ${type} notification to ${recipientId}`);
  } catch (error) {
    console.error(`[Notification] Failed to publish notification: ${error.message}`);
  }
};

module.exports = { publishNotification };
