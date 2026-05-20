const { connectRabbitMQ, getChannel } = require("../config/rabbitmq");
const ContentReviewRequest = require("../dtos/ContentReviewRequest");
const logger = require("../utils/logger");

const MODERATION_EXCHANGE =
  process.env.MODERATION_RABBITMQ_EXCHANGE || "moderation.events";
const REVIEW_REQUEST_ROUTING_KEY =
  process.env.MODERATION_REVIEW_REQUEST_ROUTING_KEY ||
  "moderation.review.requests.queue";

let topologyReady = false;

const ensureChannel = async () => {
  let channel = getChannel();
  if (!channel) {
    const connected = await connectRabbitMQ();
    channel = connected.channel;
  }

  if (!topologyReady) {
    await channel.assertExchange(MODERATION_EXCHANGE, "direct", {
      durable: true,
    });
    topologyReady = true;
  }

  return channel;
};

const publishMessageForReview = async (
  messageId,
  senderId,
  textContent,
  conversationId,
) => {
  try {
    const normalizedMessageId = String(messageId || "").trim();
    const normalizedText = String(textContent || "").trim();

    if (!normalizedMessageId || !senderId || !normalizedText) {
      logger.warn(
        `[moderation] skipped publish because messageId, senderId, or textContent is blank: messageId=${normalizedMessageId}`,
      );
      return;
    }

    const channel = await ensureChannel();
    const event = ContentReviewRequest.build({
      requestId: `chat-message:${normalizedMessageId}`,
      sourceService: "chat-service",
      eventType: "message.created",
      contentType: "TEXT",
      contentRefId: normalizedMessageId,
      userId: String(senderId),
      tenantId: "default",
      payload: {
        text: normalizedText,
      },
      metadata: {
        conversationId: String(conversationId || ""),
      },
      createdAt: new Date().toISOString(),
    });

    channel.publish(
      MODERATION_EXCHANGE,
      REVIEW_REQUEST_ROUTING_KEY,
      Buffer.from(JSON.stringify(event)),
      {
        persistent: true,
        contentType: "application/json",
      },
    );

    logger.info(
      `[moderation] review request published: requestId=${event.requestId}, messageId=${normalizedMessageId}`,
    );
  } catch (error) {
    logger.error("[moderation] publish review request failed", error);
  }
};

module.exports = {
  publishMessageForReview,
};
