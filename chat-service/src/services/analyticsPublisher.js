const crypto = require("crypto");
const { publishToQueue } = require("../config/rabbitmq");

const MESSAGE_SENT_QUEUE =
  process.env.ANALYTICS_MESSAGE_SENT_QUEUE || "analytics.message.sent.queue";

const randomId = () => {
  if (typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return crypto.randomBytes(16).toString("hex");
};

exports.publishMessageSentEvent = async ({
  messageId,
  userId,
  messageType,
}) => {
  const payload = {
    event_id: randomId(),
    message_id: String(messageId),
    user_id: String(userId),
    message_type: String(messageType || "text"),
    timestamp: new Date().toISOString(),
  };

  await publishToQueue(MESSAGE_SENT_QUEUE, payload);
};
