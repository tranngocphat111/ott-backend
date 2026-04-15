/**
 * Message Cache Service
 * Manage Redis caching for messages
 * - Keep last 20 messages for fast retrieval
 * - Update on new/edit/delete
 * - TTL: 24 hours
 */

const redis = require("redis");
const logger = require("../utils/logger");

const sanitizeAvatarValue = (value) => {
  const avatar = String(value || '').trim();
  if (!avatar) return '';

  // Never cache inline binary avatar payloads (data:image/...)
  if (/^data:image\//i.test(avatar)) {
    return '';
  }

  return avatar;
};

const sanitizeMessageForCache = (message) => {
  if (!message || typeof message !== 'object') {
    return message;
  }

  return {
    ...message,
    sender_avatar: sanitizeAvatarValue(message.sender_avatar),
  };
};

class MessageCacheService {
  constructor() {
    const redisUrl = process.env.REDIS_URL || process.env.REDIS_URI;
    const redisConfig = redisUrl
      ? { url: redisUrl }
      : {
          socket: {
            host: process.env.REDIS_HOST || "localhost",
            port: Number(process.env.REDIS_PORT || 6379),
          },
          username: process.env.REDIS_USERNAME || undefined,
          password: process.env.REDIS_PASSWORD || undefined,
          database: Number(process.env.REDIS_DB || 0),
        };

    this.client = redis.createClient(redisConfig);
    this.redisAvailable = true;
    this.connectPromise = null;

    this.client.on("error", (err) => {
      logger.error("Redis Client Error:", err);

      const message = String(err?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
    });

    this.connectPromise = this.client.connect().catch((err) => {
      logger.error("Failed to connect to Redis:", err);

      const message = String(err?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
      this.connectPromise = null;
    });

    this.CACHE_KEY_PREFIX = "messages:";
    this.MAX_MESSAGES = 20;
    this.CACHE_TTL = 86400; // 24 hours
  }

  async ensureConnected() {
    if (!this.redisAvailable) {
      return false;
    }

    try {
      if (this.client.isOpen) {
        return true;
      }

      if (!this.connectPromise) {
        this.connectPromise = this.client.connect().catch((err) => {
          logger.error("Failed to connect/reconnect Redis:", err);
          const message = String(err?.message || "");
          if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
            this.redisAvailable = false;
          }
          this.connectPromise = null;
          throw err;
        });
      }

      await this.connectPromise;
      return this.client.isOpen;
    } catch {
      return false;
    }
  }

  getMessageScore(message) {
    const createdAt =
      message?.createdAt || message?.created_at || message?.updatedAt;
    const time = new Date(createdAt).getTime();
    if (Number.isFinite(time) && time > 0) {
      return time;
    }

    return Date.now();
  }

  async removeByMsgId(conversationId, messageId) {
    if (!messageId) return 0;

    const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;
    const messages = await this.client.zRange(key, 0, -1);
    let removedCount = 0;

    for (const msgJson of messages) {
      let parsed;
      try {
        parsed = JSON.parse(msgJson);
      } catch {
        continue;
      }

      if (
        String(parsed?.msg_id || "") === String(messageId) ||
        String(parsed?._id || "") === String(messageId)
      ) {
        await this.client.zRem(key, msgJson);
        removedCount += 1;
      }
    }

    return removedCount;
  }

  /**
   * Get all cached messages for a conversation (oldest to newest)
   * @param {string} conversationId
   * @returns {Promise<Array>} messages array
   */
  async getCachedMessages(conversationId) {
    if (!(await this.ensureConnected())) {
      return [];
    }

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

      // Keep only one item per msg_id/_id to avoid duplicate renders.
      const dedup = new Map();
      parsed.forEach((message, index) => {
        if (!message) return;
        const stableId = String(
          message.msg_id || message._id || `idx:${index}`,
        );
        dedup.set(stableId, message);
      });

      return Array.from(dedup.values());
    } catch (error) {
      logger.error(
        `Error getting cached messages for ${conversationId}:`,
        error,
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
    if (!(await this.ensureConnected())) {
      return false;
    }

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
    if (!(await this.ensureConnected())) {
      return false;
    }

    try {
      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      const score = this.getMessageScore(message);

      // Ensure the same message id appears only once in cache.
      await this.removeByMsgId(conversationId, message.msg_id || message._id);

      const sanitizedMessage = sanitizeMessageForCache(message);

      // Step 1: Add message to ZSET (score = Snowflake ID)
      await this.client.zAdd(key, {
        score,
        value: JSON.stringify(sanitizedMessage),
      });

      logger.info(
        `✓ Message added to cache. Conversation: ${conversationId}, Message ID: ${message.msg_id}`,
      );

      // Step 2: Trim to keep only 20 latest messages
      const messageCount = await this.client.zCard(key);

      if (messageCount > this.MAX_MESSAGES) {
        const toRemove = messageCount - this.MAX_MESSAGES;
        await this.client.zRemRangeByRank(key, 0, toRemove - 1);
        logger.info(
          `✓ Cache trimmed: Removed ${toRemove} old messages. Total: ${this.MAX_MESSAGES}`,
        );
      }

      // Step 3: Set TTL (24 hours)
      await this.client.expire(key, this.CACHE_TTL);

      return true;
    } catch (error) {
      logger.error(
        `Error adding message to cache for ${conversationId}:`,
        error,
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
    if (!(await this.ensureConnected())) {
      return false;
    }

    try {
      if (!messages || messages.length === 0) {
        return true;
      }

      const key = `${this.CACHE_KEY_PREFIX}${conversationId}`;

      // Build ZADD dataset using msg_id (Snowflake) as score
      const uniqueById = new Map();
      messages.forEach((msg, index) => {
        const stableId = String(msg?.msg_id || msg?._id || `idx:${index}`);
        uniqueById.set(stableId, msg);
      });

      const uniqueMessages = Array.from(uniqueById.values());

      const zadd = uniqueMessages.map((msg) => {
        const score = this.getMessageScore(msg);
        const sanitizedMessage = sanitizeMessageForCache(msg);
        return {
          score,
          value: JSON.stringify(sanitizedMessage),
        };
      });

      if (zadd.length === 0) {
        logger.warn(`No valid messages to cache for ${conversationId}`);
        return false;
      }

      // Add all messages
      await this.client.zAdd(key, zadd);

      logger.info(
        `✓ Added ${zadd.length} messages to cache for ${conversationId}`,
      );

      // Trim to keep only 20 latest
      const messageCount = await this.client.zCard(key);
      if (messageCount > this.MAX_MESSAGES) {
        const toRemove = messageCount - this.MAX_MESSAGES;
        await this.client.zRemRangeByRank(key, 0, toRemove - 1);
        logger.info(
          `✓ Cache trimmed after bulk insert: ${this.MAX_MESSAGES} messages kept`,
        );
      }

      // Set TTL
      await this.client.expire(key, this.CACHE_TTL);

      return true;
    } catch (error) {
      logger.error(
        `Error adding multiple messages for ${conversationId}:`,
        error,
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
    if (!(await this.ensureConnected())) {
      return false;
    }

    try {
      const normalizedMessageId = String(messageId || '');

      // 1. Remove old message
      const removed = await this.removeMessage(conversationId, normalizedMessageId);

      if (!removed) {
        logger.warn(`Message ${normalizedMessageId} not found in cache, fallback to upsert`);
      }

      // 2. Add updated message (upsert behavior)
      await this.addMessage(conversationId, updatedMessage);

      logger.info(`✓ Message ${normalizedMessageId} updated in cache`);
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
    if (!(await this.ensureConnected())) {
      return false;
    }

    try {
      const removed = await this.removeByMsgId(conversationId, messageId);
      if (removed > 0) {
        logger.info(
          `✓ Message ${messageId} removed from cache (${removed} item)`,
        );
      }
      return removed > 0;
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
    if (!(await this.ensureConnected())) {
      return false;
    }

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
    if (!(await this.ensureConnected())) {
      return {
        conversationId,
        messageCount: 0,
        maxMessages: this.MAX_MESSAGES,
        ttlSeconds: null,
        isCached: false,
        isEmpty: true,
        redisAvailable: false,
      };
    }

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
        redisAvailable: true,
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
