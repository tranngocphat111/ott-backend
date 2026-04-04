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

const express = require('express');
const router = express.Router();
const messageRepository = require('../repositories/messageRepository');
const messageCacheService = require('../services/messageCacheService');
const logger = require('../utils/logger');

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
router.get('/conversations/:conversationId/messages', async (req, res) => {
  try {
    const { conversationId } = req.params;

    logger.info(
      `📥 GET /conversations/${conversationId}/messages - Loading latest 20`
    );

    // Get messages (from cache or DB)
    const messages = await messageRepository.getConversationMessages(
      conversationId,
      20
    );

    // Determine source (cache or DB)
    const cacheStats = await messageCacheService.getCacheStats(
      conversationId
    );
    const source = cacheStats.isCached ? 'cache' : 'database';

    res.json({
      success: true,
      conversationId,
      messageCount: messages.length,
      source,
      messages,
    });
  } catch (error) {
    logger.error('Error loading messages:', error);
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

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
router.get('/conversations/:conversationId/messages/older', async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { before, limit = '20' } = req.query;

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
        error: 'Invalid limit',
      });
    }

    logger.info(
      `📥 GET /older - loaded older messages before msg_id: ${before}`
    );

    // Fetch from MongoDB database (older messages)
    const messages = await messageRepository.getOlderMessages(
      conversationId,
      before,
      limitNum
    );

    // Extract hasMore flag if it exists
    const hasMore = messages._hasMore !== undefined ? messages._hasMore : false;
    const result = messages.filter(m => m._hasMore === undefined); // Remove flag from array

    res.json({
      success: true,
      conversationId,
      messageCount: result.length,
      hasMore,
      messages: result,
    });
  } catch (error) {
    logger.error('Error loading older messages:', error);
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

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
router.get('/conversations/:conversationId/messages/newer', async (req, res) => {
  try {
    const { conversationId } = req.params;
    const { after, limit = '20' } = req.query;

    if (!after) {
      return res.status(400).json({
        success: false,
        error: 'Parameter "after" is required',
      });
    }

    const afterTimestamp = parseInt(after, 10);
    const limitNum = parseInt(limit, 10);

    if (isNaN(afterTimestamp) || isNaN(limitNum)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid timestamp or limit',
      });
    }

    logger.info(
      `📤 GET /newer - loaded newer messages after ${new Date(afterTimestamp).toISOString()}`
    );

    // Fetch from MongoDB database (newer messages)
    const messages = await messageRepository.getNewerMessages(
      conversationId,
      afterTimestamp,
      limitNum
    );

    res.json({
      success: true,
      conversationId,
      messageCount: messages.length,
      messages,
    });
  } catch (error) {
    logger.error('Error loading newer messages:', error);
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

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
  '/conversations/:conversationId/messages/:messageId/edit',
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;
      const { text } = req.body;

      if (!text || text.trim().length === 0) {
        return res.status(400).json({
          success: false,
          error: 'Message text is required',
        });
      }

      logger.info(`✏️  POST /edit - editing message ${messageId}`);

      // Update message (DB + cache)
      const updated = await messageRepository.updateMessage(
        messageId,
        conversationId,
        { text }
      );

      res.json({
        success: true,
        message: updated,
      });
    } catch (error) {
      logger.error('Error editing message:', error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  }
);

/**
 * DELETE /api/conversations/:conversationId/messages/:messageId
 *
 * Delete a message
 */
router.delete(
  '/conversations/:conversationId/messages/:messageId',
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;

      logger.info(`🗑️  DELETE - deleting message ${messageId}`);

      // Delete message (DB + cache)
      const deleted = await messageRepository.deleteMessage(
        messageId,
        conversationId
      );

      res.json({
        success: true,
        message: deleted,
      });
    } catch (error) {
      logger.error('Error deleting message:', error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  }
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
  '/conversations/:conversationId/messages/:messageId/reaction',
  async (req, res) => {
    try {
      const { conversationId, messageId } = req.params;
      const { userId, emoji } = req.body;

      if (!userId || !emoji) {
        return res.status(400).json({
          success: false,
          error: 'userId and emoji are required',
        });
      }

      logger.info(
        `😊 POST /reaction - adding reaction ${emoji} to message ${messageId}`
      );

      // Add reaction
      const updated = await messageRepository.addReaction(
        messageId,
        conversationId,
        userId,
        emoji
      );

      res.json({
        success: true,
        message: updated,
      });
    } catch (error) {
      logger.error('Error adding reaction:', error);
      res.status(500).json({
        success: false,
        error: error.message,
      });
    }
  }
);

/**
 * GET /api/conversations/:conversationId/messages/stats
 *
 * Get cache statistics for a conversation
 */
router.get('/conversations/:conversationId/messages/stats', async (req, res) => {
  try {
    const { conversationId } = req.params;

    const stats = await messageCacheService.getCacheStats(conversationId);
    const totalCount = await messageRepository.getMessageCount(conversationId);

    res.json({
      success: true,
      stats: {
        ...stats,
        totalMessagesInDB: totalCount,
      },
    });
  } catch (error) {
    logger.error('Error getting stats:', error);
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

module.exports = router;
