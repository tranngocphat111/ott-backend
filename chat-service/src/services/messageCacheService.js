/**
 * Message Cache Service
 * Manage Redis caching for messages
 * - Keep last 20 messages for fast retrieval
 * - Update on new/edit/delete
 * - TTL: 24 hours
 */

const redis = require('redis');
const logger = require('../utils/logger');

class MessageCacheService {
  constructor() {
    this.client = redis.createClient({
      host: process.env.REDIS_HOST || 'localhost',
      port: process.env.REDIS_PORT || 6379,
      password: process.env.REDIS_PASSWORD,
      db: 0,
    });

    this.client.on('error', (err) => {
      logger.error('Redis Client Error:', err);
    });

    this.client.connect().catch((err) => {
      logger.error('Failed to connect to Redis:', err);
    });

    this.CACHE_KEY_PREFIX = 'messages:';
    this.MAX_MESSAGES = 20;
    this.CACHE_TTL = 86400; // 24 hours
  }

  /**
   * Get all cached messages for a conversation (oldest to newest)
   * @param {string} conversationId
   * @returns {Promise<Array>} messages array
   */
  async getCachedMessages(conversationId) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      // ZRANGE: Get all members (oldest to newest by score)
      const messages = await this.client.zRange(key, 0, -1);

      const parsed = messages.map((msg) => {
        try {
          return JSON.parse(msg);
        } catch (e) {
          logger.error(`Failed to parse message: ${msg}`, e);
          return null;
        }
      });

      return parsed.filter((m) => m !== null);
    } catch (error) {
      logger.error(
        `Error getting cached messages for ${conversationId}:`,
        error
      );
      return [];
    }
  }

  /**
   * Check if conversation cache exists
   * @param {string} conversationId
   * @returns {Promise<boolean>}
   */
  async cacheExists(conversationId) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;
      const count = await this.client.zCard(key);
      return count > 0;
    } catch (error) {
      logger.error(`Error checking cache for ${conversationId}:`, error);
      return false;
    }
  }

  /**
   * Add a new message to cache
   * - Add to ZSET with msg_id (Snowflake ID) as score
   * - Keep only last 20 messages
   * - Set TTL
   *
   * @param {string} conversationId
   * @param {Object} message - message object with msg_id
   * @returns {Promise<boolean>}
   */
  async addMessage(conversationId, message) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      // Use msg_id (Snowflake) as score - already ordered by time!
      const score = Number(message.msg_id);

      if (isNaN(score)) {
        throw new Error(`Invalid msg_id: ${message.msg_id}`);
      }

      // Step 1: Add message to ZSET (score = Snowflake ID)
      await this.client.zAdd(key, {
        score,
        value: JSON.stringify(message),
      });

      logger.info(
        `✓ Message added to cache. Conversation: ${conversationId}, Message ID: ${message.msg_id}`
      );

      // Step 2: Trim to keep only 20 latest messages
      const messageCount = await this.client.zCard(key);

      if (messageCount > this.MAX_MESSAGES) {
        const toRemove = messageCount - this.MAX_MESSAGES;
        await this.client.zRemRangeByRank(key, 0, toRemove - 1);
        logger.info(
          `✓ Cache trimmed: Removed ${toRemove} old messages. Total: ${this.MAX_MESSAGES}`
        );
      }

      // Step 3: Set TTL (24 hours)
      await this.client.expire(key, this.CACHE_TTL);

      return true;
    } catch (error) {
      logger.error(
        `Error adding message to cache for ${conversationId}:`,
        error
      );
      return false;
    }
  }

  /**
   * Add multiple messages at once (bulk insert for initialization)
   * @param {string} conversationId
   * @param {Array} messages - array of message objects
   * @returns {Promise<boolean>}
   */
  async addMultipleMessages(conversationId, messages) {
    try {
      if (!messages || messages.length === 0) {
        return true;
      }

      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      // Build ZADD dataset using msg_id (Snowflake) as score
      const zadd = messages.map((msg) => {
        const score = Number(msg.msg_id);
        if (isNaN(score)) {
          logger.warn(`Invalid msg_id: ${msg.msg_id}, skipping`);
          return null;
        }
        return {
          score,
          value: JSON.stringify(msg),
        };
      }).filter(item => item !== null);

      if (zadd.length === 0) {
        logger.warn(`No valid messages to cache for ${conversationId}`);
        return false;
      }

      // Add all messages
      await this.client.zAdd(key, zadd);

      logger.info(
        `✓ Added ${zadd.length} messages to cache for ${conversationId}`
      );

      // Trim to keep only 20 latest
      const messageCount = await this.client.zCard(key);
      if (messageCount > this.MAX_MESSAGES) {
        const toRemove = messageCount - this.MAX_MESSAGES;
        await this.client.zRemRangeByRank(key, 0, toRemove - 1);
        logger.info(
          `✓ Cache trimmed after bulk insert: ${this.MAX_MESSAGES} messages kept`
        );
      }

      // Set TTL
      await this.client.expire(key, this.CACHE_TTL);

      return true;
    } catch (error) {
      logger.error(
        `Error adding multiple messages for ${conversationId}:`,
        error
      );
      return false;
    }
  }

  /**
   * Update a message in cache (edit)
   * @param {string} conversationId
   * @param {string} messageId
   * @param {Object} updatedMessage
   * @returns {Promise<boolean>}
   */
  async updateMessage(conversationId, messageId, updatedMessage) {
    try {
      // 1. Remove old message
      const removed = await this.removeMessage(conversationId, messageId);

      if (!removed) {
        logger.warn(`Message ${messageId} not found in cache`);
        return false;
      }

      // 2. Add updated message
      await this.addMessage(conversationId, updatedMessage);

      logger.info(`✓ Message ${messageId} updated in cache`);
      return true;
    } catch (error) {
      logger.error(`Error updating message in cache:`, error);
      return false;
    }
  }

  /**
   * Remove a message from cache (delete)
   * @param {string} conversationId
   * @param {string} messageId
   * @returns {Promise<boolean>}
   */
  async removeMessage(conversationId, messageId) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      // Get all messages
      const messages = await this.client.zRange(key, 0, -1);

      // Find and remove the target message
      for (const msgJson of messages) {
        const msg = JSON.parse(msgJson);
        if (msg.msg_id === messageId || msg._id === messageId) {
          await this.client.zRem(key, msgJson);
          logger.info(`✓ Message ${messageId} removed from cache`);
          return true;
        }
      }

      return false;
    } catch (error) {
      logger.error(`Error removing message from cache:`, error);
      return false;
    }
  }

  /**
   * Clear entire cache for a conversation
   * @param {string} conversationId
   * @returns {Promise<boolean>}
   */
  async clearCache(conversationId) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;
      await this.client.del(key);
      logger.info(`✓ Cache cleared for conversation ${conversationId}`);
      return true;
    } catch (error) {
      logger.error(`Error clearing cache:`, error);
      return false;
    }
  }

  /**
   * Get cache statistics
   * @param {string} conversationId
   * @returns {Promise<Object>}
   */
  async getCacheStats(conversationId) {
    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;
      const count = await this.client.zCard(key);
      const ttl = await this.client.ttl(key);

      return {
        conversationId,
        messageCount: count,
        maxMessages: this.MAX_MESSAGES,
        ttlSeconds: ttl === -1 ? null : ttl,
        isCached: count > 0,
        isEmpty: count === 0,
      };
    } catch (error) {
      logger.error(`Error getting cache stats:`, error);
      return {
        error: error.message,
      };
    }
  }
}

module.exports = new MessageCacheService();
