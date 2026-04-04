/**
 * Message Repository
 * Handle database operations & cache synchronization
 * - Save to MongoDB
 * - Cache in Redis (last 20)
 * - Load older messages from MongoDB (pagination)
 */

const Message = require('../models/Message');
const messageCacheService = require('../services/messageCacheService');
const logger = require('../utils/logger');

class MessageRepository {
  /**
   * Create and save a new message
   * 1. Save to MongoDB
   * 2. Add to Redis cache
   *
   * @param {string} conversationId
   * @param {string} userId
   * @param {Object} messageData - {text, content, type, etc}
   * @returns {Promise<Object>} saved message
   */
  async create(conversationId, userId, messageData) {
    try {
      // Step 1: Create message object (match actual schema!)
      const message = new Message({
        conversation_id: conversationId, // Match schema field name!
        sender_id: userId, // Match schema field name!
        content: [messageData.text || messageData.content], // Schema expects array
        type: messageData.type || 'text',
      });

      // Step 2: Save to MongoDB
      const savedMessage = await message.save();
      const messageObj = savedMessage.toObject();

      logger.info(`📝 Message created in DB: ${messageObj.msg_id}`);

      // Step 3: Add to Redis cache
      await messageCacheService.addMessage(conversationId, messageObj);

      return messageObj;
    } catch (error) {
      logger.error('Error creating message:', error);
      throw error;
    }
  }

  /**
   * Get message history for a conversation
   * - Try Redis cache first (last 20)
   * - If miss, fetch from MongoDB & cache
   *
   * @param {string} conversationId
   * @param {number} limit - number of messages to retrieve
   * @returns {Promise<Array>} messages (oldest to newest)
   */
  async getConversationMessages(conversationId, limit = 20) {
    try {
      // Step 1: Check Redis cache
      const cachedExists = await messageCacheService.cacheExists(
        conversationId
      );

      if (cachedExists) {
        const messages = await messageCacheService.getCachedMessages(
          conversationId
        );

        if (messages && messages.length > 0) {
          logger.info(
            `✓ CACHE HIT: ${messages.length} messages for ${conversationId}`
          );
          return messages;
        }
      }

      // Step 2: Cache miss - fetch from MongoDB
      logger.info(`✗ CACHE MISS: Fetching from MongoDB for ${conversationId}`);

      const messages = await Message.find({ conversation_id: conversationId })
        .sort({ createdAt: -1 })
        .limit(limit)
        .lean();

      if (messages.length === 0) {
        logger.info(`No messages found for ${conversationId}`);
        return [];
      }

      // Step 3: Reverse to get oldest → newest order
      const orderedMessages = messages.reverse();

      // Step 4: Cache the results
      await messageCacheService.addMultipleMessages(
        conversationId,
        orderedMessages
      );

      logger.info(
        `✓ Cached ${orderedMessages.length} messages from MongoDB`
      );

      return orderedMessages;
    } catch (error) {
      logger.error('Error getting messages:', error);
      throw error;
    }
  }

  /**
   * Get older messages (for pagination when user scrolls up)
   * - Fetch messages BEFORE a given msg_id (Snowflake)
   * - Return in oldest → newest order
   *
   * @param {string} conversationId
   * @param {string|number} beforeMsgId - Snowflake ID to fetch messages before
   * @param {number} limit - number of messages to fetch
   * @returns {Promise<Array>} older messages
   */
  async getOlderMessages(conversationId, beforeMsgId, limit = 20) {
    try {
      logger.info(
        `📥 Loading older messages for ${conversationId}, before msg_id: ${beforeMsgId}`
      );

      // Convert to number for comparison
      const beforeId = Number(beforeMsgId);

      // Fetch from MongoDB (messages with msg_id < beforeMsgId)
      const messages = await Message.find({
        conversation_id: conversationId,
        msg_id: { $lt: beforeId }, // Snowflake IDs are comparable as numbers
      })
        .sort({ msg_id: -1 }) // newest first (largest ID first)
        .limit(limit + 1) // +1 to check if more exist
        .lean();

      if (messages.length === 0) {
        logger.info(`No older messages found for ${conversationId}`);
        return [];
      }

      // Determine if more messages exist
      let result = messages;
      let hasMore = false;

      if (messages.length > limit) {
        hasMore = true;
        result = messages.slice(0, limit);
      }

      // Reverse to get oldest → newest
      const orderedMessages = result.reverse();

      logger.info(
        `✓ Loaded ${orderedMessages.length} older messages from DB (hasMore: ${hasMore})`
      );

      // Store hasMore for response
      orderedMessages._hasMore = hasMore;

      return orderedMessages;
    } catch (error) {
      logger.error('Error getting older messages:', error);
      throw error;
    }
  }

  /**
   * Get newer messages (for pagination when user scrolls down)
   * - Fetch messages AFTER a given msg_id
   *
   * @param {string} conversationId
   * @param {string|number} afterMsgId - Snowflake ID to fetch messages after
   * @param {number} limit - number of messages to fetch
   * @returns {Promise<Array>} newer messages
   */
  async getNewerMessages(conversationId, afterMsgId, limit = 20) {
    try {
      logger.info(
        `📤 Loading newer messages for ${conversationId}, after msg_id: ${afterMsgId}`
      );

      // Convert to number for comparison
      const afterId = Number(afterMsgId);

      const messages = await Message.find({
        conversation_id: conversationId,
        msg_id: { $gt: afterId }, // Snowflake IDs are comparable
      })
        .sort({ msg_id: 1 }) // oldest first (smallest ID first)
        .limit(limit)
        .lean();

      if (messages.length === 0) {
        logger.info(`No newer messages found for ${conversationId}`);
        return [];
      }

      logger.info(`✓ Loaded ${messages.length} newer messages from DB`);

      return messages;
    } catch (error) {
      logger.error('Error getting newer messages:', error);
      throw error;
    }
  }

  /**
   * Update a message (edit)
   *
   * @param {string} messageId (msg_id)
   * @param {string} conversationId
   * @param {Object} updates - {text, content, etc}
   * @returns {Promise<Object>} updated message
   */
  async updateMessage(messageId, conversationId, updates) {
    try {
      // Parse text to content array if provided
      const updateData = { ...updates };
      if (updates.text) {
        updateData.content = [updates.text];
        delete updateData.text;
      }

      // Update in MongoDB
      const updated = await Message.findOneAndUpdate(
        { msg_id: messageId, conversation_id: conversationId }, // Match schema!
        updateData,
        { new: true }
      ).lean();

      if (!updated) {
        throw new Error(`Message ${messageId} not found`);
      }

      logger.info(`✏️  Message ${messageId} updated in DB`);

      // Update in Redis cache
      await messageCacheService.updateMessage(
        conversationId,
        messageId,
        updated
      );

      return updated;
    } catch (error) {
      logger.error('Error updating message:', error);
      throw error;
    }
  }

  /**
   * Delete a message (soft delete)
   *
   * @param {string} messageId (msg_id)
   * @param {string} conversationId
   * @returns {Promise<Object>} deleted message
   */
  async deleteMessage(messageId, conversationId) {
    try {
      // Soft delete in MongoDB (mark as deleted)
      const deleted = await Message.findOneAndUpdate(
        { msg_id: messageId, conversation_id: conversationId }, // Match schema!
        { is_deleted: true },
        { new: true }
      ).lean();

      if (!deleted) {
        throw new Error(`Message ${messageId} not found`);
      }

      logger.info(`🗑️  Message ${messageId} deleted (soft delete in DB)`);

      // Remove from Redis cache
      await messageCacheService.removeMessage(conversationId, messageId);

      return deleted;
    } catch (error) {
      logger.error('Error deleting message:', error);
      throw error;
    }
  }

  /**
   * Add reaction to a message
   *
   * @param {string} messageId (msg_id)
   * @param {string} conversationId
   * @param {string} userId
   * @param {string} emoji
   * @returns {Promise<Object>} updated message
   */
  async addReaction(messageId, conversationId, userId, emoji) {
    try {
      // Get current message
      const message = await Message.findOne({
        msg_id: messageId,
        conversation_id: conversationId,
      });

      if (!message) {
        throw new Error('Message not found');
      }

      // Check if reaction already exists
      const reactionIndex = message.reactions.findIndex(
        (r) => r.user_id === userId && r.type === emoji
      );

      if (reactionIndex === -1) {
        // Add new reaction
        message.reactions.push({ user_id: userId, type: emoji });
      }

      // Save to MongoDB
      await message.save();
      const updated = message.toObject();

      logger.info(`😊 Reaction ${emoji} added to message ${messageId}`);

      // Update Redis cache
      await messageCacheService.updateMessage(
        conversationId,
        messageId,
        updated
      );

      return updated;
    } catch (error) {
      logger.error('Error adding reaction:', error);
      throw error;
    }
  }

  /**
   * Get message count for a conversation
   *
   * @param {string} conversationId
   * @returns {Promise<number>} total message count
   */
  async getMessageCount(conversationId) {
    try {
      const count = await Message.countDocuments({
        conversation_id: conversationId,
      });
      return count;
    } catch (error) {
      logger.error('Error getting message count:', error);
      return 0;
    }
  }
}

module.exports = new MessageRepository();
