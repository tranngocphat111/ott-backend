/**
 * Message Routes
 * API endpoints for message retrieval and management
 *
 * Endpoints:
 * - GET /api/conversations/{conversationId}/messages
 *   Load latest 20 messages (from Redis)
 *
 * - GET /api/conversations/{conversationId}/messages/older
 *   Load older messages when scrolling up (from MongoDB)
 *
 * - POST /api/conversations/{conversationId}/messages/{messageId}/edit
 *   Edit a message
 *
 * - DELETE /api/conversations/{conversationId}/messages/{messageId}
 *   Delete a message
 */

const express = require("express");
const router = express.Router();
const messageRepository = require("../repositories/messageRepository");
const MessageService = require("../services/messageService");
const messageCacheService = require("../services/messageCacheService");
const logger = require("../utils/logger");

router.get("/media/download", async (req, res) => {
  try {
    const { fileUrl, fileName } = req.query;

    if (!fileUrl) {
      return res.status(400).json({
        success: false,
        error: 'Parameter "fileUrl" is required',
      });
    }

    const sourceUrl = String(fileUrl);
    if (!/^https?:\/\//i.test(sourceUrl)) {
      return res.status(400).json({
        success: false,
        error: "Only http/https URLs are allowed",
      });
    }

    const upstream = await fetch(sourceUrl);
    if (!upstream.ok) {
      return res.status(upstream.status).json({
        success: false,
        error: `Cannot fetch source file (${upstream.status})`,
      });
    }

    const inferredName =
      String(fileName || "").trim() ||
      sourceUrl.split("/").pop()?.split("?")[0] ||
      `download-${Date.now()}`;

    const contentType =
      upstream.headers.get("content-type") || "application/octet-stream";

    const buffer = Buffer.from(await upstream.arrayBuffer());

    res.setHeader("Content-Type", contentType);
    res.setHeader(
      "Content-Disposition",
      `attachment; filename="${encodeURIComponent(inferredName)}"`,
    );
    res.setHeader("Content-Length", String(buffer.length));

    return res.status(200).send(buffer);
  } catch (error) {
    logger.error("Error downloading media:", error);
    return res.status(500).json({
      success: false,
      error: "Failed to download media",
    });
  }
});

/**
 * GET /api/conversations/:conversationId/messages
 *
 * Load latest 20 messages (from Redis cache)
 * This is called when user opens a conversation
 *
 * Response:
 * {
 *   success: true,
 *   conversationId: "conv123",
 *   messageCount: 20,
 *   source: "cache" | "database",
 *   messages: [...]
 * }
 */
router.get("/conversations/:conversationId/messages", async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { userId } = req.query;

    logger.info(
      `📥 GET /conversations/${conversationId}/messages - Loading latest 20`,
    );

    // Get messages (from cache or DB)
    const messages = await messageRepository.getConversationMessages(
      conversationId,
      20,
      userId,
    );

    // Determine source (cache or DB)
    const cacheStats = await messageCacheService.getCacheStats(conversationId);
    const source = cacheStats.isCached ? "cache" : "database";

    res.json({
      success: true,
      conversationId,
      messageCount: messages.length,
      source,
      messages,
    });
  } catch (error) {
    logger.error("Error loading messages:", error);
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

/**
 * GET /api/conversations/:conversationId/messages/around
 *
 * Load message context around target message.
 * Query:
 * - messageId (required)
 * - before (default: 20)
 * - after (default: 20)
 * - userId (optional)
 */
router.get(
  "/conversations/:conversationId/messages/around",
  async (req, res) => {
    try {
      const { conversationId } = req.params;
      const { messageId, before = "20", after = "20", userId } = req.query;

      if (!messageId) {
        return res.status(400).json({
          success: false,
          error: 'Parameter "messageId" is required',
        });
      }

      const beforeNum = parseInt(before, 10);
      const afterNum = parseInt(after, 10);

      if (isNaN(beforeNum) || isNaN(afterNum)) {
        return res.status(400).json({
          success: false,
          error: "Invalid before/after value",
        });
      }

      const context = await messageRepository.getMessageContext(
        conversationId,
        messageId,
        beforeNum,
        afterNum,
        userId,
      );

      if (!context) {
        return res.status(404).json({
          success: false,
          error: "Message not found",
        });
      }

      res.json({
        success: true,
        conversationId,
        ...context,
      });
    } catch (error) {
      logger.error("Error loading message context:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * GET /api/conversations/:conversationId/messages/older
 *
 * Load older messages when user scrolls up
 * This queries MongoDB directly (pagination)
 *
 * Query Parameters:
 * - before: msg_id to fetch messages before (Snowflake ID)
 * - limit: number of messages to fetch (default: 20)
 *
 * Example:
 * GET /api/conversations/conv123/messages/older?before=123456789&limit=20
 */
router.get(
  "/conversations/:conversationId/messages/older",
  async (req, res) => {
    try {
      const { conversationId } = req.params;
      const { before, limit = "20", userId } = req.query;

      if (!before) {
        return res.status(400).json({
          success: false,
          error: 'Parameter "before" (msg_id) is required',
        });
      }

      const limitNum = parseInt(limit, 10);

      if (isNaN(limitNum)) {
        return res.status(400).json({
          success: false,
          error: "Invalid limit",
        });
      }

      logger.info(
        `📥 GET /older - loaded older messages before msg_id: ${before}`,
      );

      // Fetch from MongoDB database (older messages)
      const messages = await messageRepository.getOlderMessages(
        conversationId,
        before,
        limitNum,
        userId,
      );

      // Extract hasMore flag if it exists
      const hasMore =
        messages._hasMore !== undefined ? messages._hasMore : false;
      const result = messages.filter((m) => m._hasMore === undefined); // Remove flag from array

      res.json({
        success: true,
        conversationId,
        messageCount: result.length,
        hasMore,
        messages: result,
      });
    } catch (error) {
      logger.error("Error loading older messages:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * GET /api/conversations/:conversationId/messages/newer
 *
 * Load newer messages when user scrolls down
 * (For gap filling when new messages arrive while user is viewing)
 *
 * Query Parameters:
 * - after: timestamp of the newest message in current view (required)
 * - limit: number of messages to fetch (default: 20)
 */
router.get(
  "/conversations/:conversationId/messages/newer",
  async (req, res) => {
    try {
      const { conversationId } = req.params;
      const { after, limit = "20", userId } = req.query;

      if (!after) {
        return res.status(400).json({
          success: false,
          error: 'Parameter "after" is required',
        });
      }

      const afterMsgId = String(after);
      const limitNum = parseInt(limit, 10);

      if (!afterMsgId || isNaN(limitNum)) {
        return res.status(400).json({
          success: false,
          error: "Invalid after or limit",
        });
      }

      logger.info(`📤 GET /newer - loaded newer messages after msg_id: ${afterMsgId}`);

      // Fetch from MongoDB database (newer messages)
      const messages = await messageRepository.getNewerMessages(
        conversationId,
        afterMsgId,
        limitNum,
        userId,
      );

      res.json({
        success: true,
        conversationId,
        messageCount: messages.length,
        messages,
      });
    } catch (error) {
      logger.error("Error loading newer messages:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * POST /api/conversations/:conversationId/messages/:messageId/edit
 *
 * Edit a message
 *
 * Body:
 * {
 *   text: "Updated message text"
 * }
 */
router.post(
  "/conversations/:conversationId/messages/:messageId/edit",
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;
      const { text } = req.body;

      if (!text || text.trim().length === 0) {
        return res.status(400).json({
          success: false,
          error: "Message text is required",
        });
      }

      logger.info(`✏️  POST /edit - editing message ${messageId}`);

      // Update message (DB + cache)
      const updated = await messageRepository.updateMessage(
        messageId,
        conversationId,
        { text },
      );

      res.json({
        success: true,
        message: updated,
      });
    } catch (error) {
      logger.error("Error editing message:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * DELETE /api/conversations/:conversationId/messages/:messageId
 *
 * Delete a message
 */
router.delete(
  "/conversations/:conversationId/messages/:messageId",
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;
      const { userId } = req.body;

      if (!userId) {
        return res.status(400).json({
          success: false,
          error: "userId is required",
        });
      }

      logger.info(`🗑️  DELETE - deleting message ${messageId}`);

      // Delete message only for current user
      const deleted = await MessageService.deleteMessage({
        conversationId,
        msgId: messageId,
        userId,
      });

      res.json({
        success: true,
        message: deleted,
      });
    } catch (error) {
      logger.error("Error deleting message:", error);
      if (
        error.message === "Tin nhắn không tồn tại" ||
        error.message === "Bạn không thuộc cuộc hội thoại này"
      ) {
        return res.status(400).json({
          success: false,
          error: error.message,
        });
      }

      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * POST /api/conversations/:conversationId/messages/:messageId/reaction
 *
 * Add reaction to a message
 *
 * Body:
 * {
 *   userId: "user123",
 *   emoji: "👍"
 * }
 */
router.post(
  "/conversations/:conversationId/messages/:messageId/reaction",
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;
      const { userId, emoji } = req.body;

      if (!userId || !emoji) {
        return res.status(400).json({
          success: false,
          error: "userId and emoji are required",
        });
      }

      logger.info(
        `😊 POST /reaction - adding reaction ${emoji} to message ${messageId}`,
      );

      // Add reaction
      const updated = await messageRepository.addReaction(
        messageId,
        conversationId,
        userId,
        emoji,
      );

      res.json({
        success: true,
        message: updated,
      });
    } catch (error) {
      logger.error("Error adding reaction:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

/**
 * GET /api/conversations/:conversationId/messages/stats
 *
 * Get cache statistics for a conversation
 */
router.get(
  "/conversations/:conversationId/messages/stats",
  async (req, res) => {
    try {
      const { conversationId } = req.params;

      const stats = await messageCacheService.getCacheStats(conversationId);
      const totalCount =
        await messageRepository.getMessageCount(conversationId);

      res.json({
        success: true,
        stats: {
          ...stats,
          totalMessagesInDB: totalCount,
        },
      });
    } catch (error) {
      logger.error("Error getting stats:", error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  },
);

module.exports = router;
