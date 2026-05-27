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

const publishMessageImageForReview = async ({
  messageId,
  senderId,
  objectKey,
  imageIndex,
  conversationId,
  bucketName,
}) => {
  try {
    const normalizedMessageId = String(messageId || "").trim();
    const normalizedObjectKey = String(objectKey || "").trim();
    const normalizedBucketName = String(bucketName || "").trim();
    const normalizedImageIndex = Number.isFinite(Number(imageIndex))
      ? Number(imageIndex)
      : 0;

    if (
      !normalizedMessageId ||
      !senderId ||
      !normalizedObjectKey ||
      !normalizedBucketName
    ) {
      logger.warn(
        `[moderation] skipped image review publish because messageId, senderId, objectKey, or bucketName is blank: messageId=${normalizedMessageId}`,
      );
      return;
    }

    const channel = await ensureChannel();
    const event = ContentReviewRequest.build({
      requestId: `chat-image:${normalizedMessageId}:${normalizedImageIndex}`,
      sourceService: "chat-service",
      eventType: "message.image.created",
      contentType: "IMAGE",
      contentRefId: normalizedMessageId,
      userId: String(senderId),
      tenantId: "default",
      payload: {
        bucket: normalizedBucketName,
        objectKey: normalizedObjectKey,
      },
      metadata: {
        conversationId: String(conversationId || ""),
        messageId: normalizedMessageId,
        imageIndex: normalizedImageIndex,
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
      `[moderation] image review request published: requestId=${event.requestId}, messageId=${normalizedMessageId}, imageIndex=${normalizedImageIndex}`,
    );
  } catch (error) {
    logger.error("[moderation] publish image review request failed", error);
  }
};

module.exports = {
  publishMessageForReview,
  publishMessageImageForReview,
};
