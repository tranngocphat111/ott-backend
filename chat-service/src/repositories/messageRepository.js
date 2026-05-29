/**
 * Message Repository
 * Handle database operations & cache synchronization
 * - Save to MongoDB
 * - Cache in Redis (last 20)
 * - Load older messages from MongoDB (pagination)
 */

const Message = require("../models/Message");
const User = require("../models/User");
const messageCacheService = require("../services/messageCacheService");
const logger = require("../utils/logger");

const getFileNameFromKey = (key) => {
  const rawName =
    String(key || "")
      .split("/")
      .pop() || "File";
  const match = rawName.match(/^[a-f0-9]+_(.+)$/i);
  return match ? match[1] : rawName;
};

const buildReplyPreview = (message, senderName = "") => {
  if (!message) return null;

  const rawContent = Array.isArray(message.content)
    ? message.content[0] || ""
    : message.content || "";

  const mediaUrls = Array.isArray(message.content)
    ? message.content.filter(Boolean).map((item) => String(item))
    : rawContent
      ? [String(rawContent)]
      : [];

  const isUrlLike = /^(https?:\/\/|www\.)/i.test(rawContent);
  const mediaUrl = rawContent && !isUrlLike ? rawContent : "";
  const fileName =
    message.type === "file" ||
    message.type === "video" ||
    message.type === "audio"
      ? getFileNameFromKey(rawContent)
      : "";

  let preview = "";
  switch (message.type) {
    case "image":
      preview = rawContent;
      break;
    case "video":
      preview = rawContent;
      break;
    case "file":
      preview = fileName || "[Tệp tin]";
      break;
    case "audio":
      preview = fileName || "[Âm thanh]";
      break;
    case "link":
      preview = rawContent;
      break;
    default:
      preview = rawContent;
      break;
  }

  return {
    msg_id: message.msg_id,
    sender_id: message.sender_id,
    sender_name: senderName || message.sender_name || "",
    type: message.type,
    content: preview.length > 120 ? preview.substring(0, 120) + "..." : preview,
    raw_content: rawContent,
    file_name: fileName,
    url: mediaUrl || rawContent,
    media_urls: message.type === "image" ? mediaUrls : undefined,
    media_count: message.type === "image" ? mediaUrls.length : undefined,
    is_deleted: !!message.is_deleted,
    is_revoked: !!message.is_revoked,
  };
};

const sanitizeAvatarValue = (value) => {
  const avatar = String(value || "").trim();
  if (!avatar) return "";
  if (/^data:image\//i.test(avatar)) return "";
  return avatar;
};

class MessageRepository {
  async hydrateSenderInfo(messages = []) {
    const senderIds = [
      ...new Set(
        messages
          .map((message) => String(message?.sender_id || ""))
          .filter(Boolean),
      ),
    ];

    if (!senderIds.length) {
      return messages;
    }

    const senders = await User.find({ user_id: { $in: senderIds } })
      .select("user_id name avatar")
      .lean();
    const senderMap = new Map(
      senders.map((sender) => [String(sender.user_id || ""), sender]),
    );

    return messages.map((message) => {
      const sender = senderMap.get(String(message.sender_id || ""));
      return {
        ...message,
        sender_name: sender?.name || message.sender_name || "",
        sender_avatar: sanitizeAvatarValue(
          sender?.avatar || message.sender_avatar || "",
        ),
      };
    });
  }

  getMessageStableId(message) {
    return String(message?.msg_id || message?._id || "").trim();
  }

  getLatestMessageId(messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
      return "";
    }

    let latest = "";
    for (const message of messages) {
      const currentId = this.getMessageStableId(message);
      if (!currentId) continue;
      if (!latest) {
        latest = currentId;
        continue;
      }

      try {
        if (BigInt(currentId) > BigInt(latest)) {
          latest = currentId;
        }
      } catch {
        if (currentId > latest) {
          latest = currentId;
        }
      }
    }

    return latest;
  }

  async getLatestVisibleMessageId(conversationId, userId) {
    const latest = await Message.findOne({
      conversation_id: conversationId,
      is_deleted: { $ne: true },
      ...(userId ? { deleted_for: { $ne: userId } } : {}),
    })
      .sort({ msg_id: -1 })
      .select("msg_id _id")
      .lean();

    return this.getMessageStableId(latest);
  }

  isVisibleToUser(message, userId, deletedMsgId = "0") {
    if (message.is_deleted) return false;
    
    // Check if message is older than the user's join point (or clear chat point)
    if (deletedMsgId && deletedMsgId !== "0" && message.msg_id) {
      if (BigInt(message.msg_id) <= BigInt(deletedMsgId)) {
        return false;
      }
    }

    if (!userId) return true;

    const deletedFor = Array.isArray(message.deleted_for)
      ? message.deleted_for
      : [];
    return !deletedFor.includes(userId);
  }

  async getDeletedMsgId(conversationId, userId) {
    if (!userId) return "0";
    try {
      const Participant = require("../models/Participant");
      const participant = await Participant.findOne({ conversation_id: conversationId, user_id: userId }).select('deleted_msg_id').lean();
      return participant?.deleted_msg_id || "0";
    } catch (e) {
      return "0";
    }
  }

  async hydrateReplyPreviews(conversationId, messages, userId) {
    const replyIds = [
      ...new Set(
        messages
          .map((m) => m.reply_to_msg_id)
          .filter(
            (replyId) => typeof replyId === "string" && replyId.length > 0,
          ),
      ),
    ];

    if (replyIds.length === 0) {
      return messages.map((m) => ({ ...m, reply_to: null }));
    }

    const referencedMessages = await Message.find({
      conversation_id: conversationId,
      msg_id: { $in: replyIds },
    }).lean();

    const referencedSenderIds = [
      ...new Set(referencedMessages.map((m) => m.sender_id)),
    ];
    const referencedSenders = await User.find({
      user_id: { $in: referencedSenderIds },
    })
      .select("user_id name")
      .lean();

    const senderNameMap = new Map(
      referencedSenders.map((user) => [user.user_id, user.name || ""]),
    );

    const referencedMap = new Map(
      referencedMessages.map((m) => {
        const preview = buildReplyPreview(
          m,
          senderNameMap.get(m.sender_id) || "",
        );

        // Hide reply preview content when target message is deleted for this user.
        if (!this.isVisibleToUser(m, userId)) {
          return [
            m.msg_id,
            {
              ...preview,
              is_deleted: true,
            },
          ];
        }

        return [m.msg_id, preview];
      }),
    );

    return messages.map((m) => ({
      ...m,
      reply_to: referencedMap.get(m.reply_to_msg_id) || null,
    }));
  }

  async hydrateLiveReactions(conversationId, messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
      return messages;
    }

    const messageIds = [
      ...new Set(
        messages
          .map((message) => String(message?.msg_id || "").trim())
          .filter(Boolean),
      ),
    ];

    if (messageIds.length === 0) {
      return messages;
    }

    const reactionDocs = await Message.find({
      conversation_id: conversationId,
      msg_id: { $in: messageIds },
    })
      .select("msg_id reactions")
      .lean();

    const reactionMap = new Map(
      reactionDocs.map((doc) => [
        String(doc.msg_id || ""),
        Array.isArray(doc.reactions) ? doc.reactions : [],
      ]),
    );

    return messages.map((message) => {
      const msgId = String(message?.msg_id || "");
      if (!reactionMap.has(msgId)) {
        return {
          ...message,
          reactions: Array.isArray(message?.reactions) ? message.reactions : [],
        };
      }

      return {
        ...message,
        reactions: reactionMap.get(msgId),
      };
    });
  }

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
        type: messageData.type || "text",
      });

      // Step 2: Save to MongoDB
      const savedMessage = await message.save();
      const messageObj = savedMessage.toObject();

      logger.info(`📝 Message created in DB: ${messageObj.msg_id}`);

      // Step 3: Add to Redis cache
      await messageCacheService.addMessage(conversationId, messageObj);

      return messageObj;
    } catch (error) {
      logger.error("Error creating message:", error);
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
  async getConversationMessages(conversationId, limit = 20, userId) {
    try {
      const deletedMsgId = await this.getDeletedMsgId(conversationId, userId);

      // Step 1: Check Redis cache
      const cachedExists =
        await messageCacheService.cacheExists(conversationId);

      if (cachedExists) {
        const messages =
          await messageCacheService.getCachedMessages(conversationId);

        if (messages && messages.length > 0) {
          logger.info(
            `✓ CACHE HIT: ${messages.length} messages for ${conversationId}`,
          );
          const visibleMessages = messages.filter((m) =>
            this.isVisibleToUser(m, userId, deletedMsgId),
          );

          if (visibleMessages.length > 0) {
            const cacheLatestId = this.getLatestMessageId(visibleMessages);
            const dbLatestId = await this.getLatestVisibleMessageId(
              conversationId,
              userId,
            );

            if (!dbLatestId || cacheLatestId === dbLatestId) {
              const liveReactionMessages = await this.hydrateLiveReactions(
                conversationId,
                visibleMessages,
              );
              const hydratedSenders =
                await this.hydrateSenderInfo(liveReactionMessages);

              return await this.hydrateReplyPreviews(
                conversationId,
                hydratedSenders,
                userId,
              );
            }

            logger.warn(
              `↺ CACHE STALE: ${conversationId} cache latest ${cacheLatestId} != db latest ${dbLatestId}. Fallback DB`,
            );
          }

          logger.info(
            `↺ CACHE FALLBACK: cache has no visible messages for user ${userId || "anonymous"}, querying MongoDB`,
          );
        }
      }

      // Step 2: Cache miss - fetch from MongoDB
      logger.info(`✗ CACHE MISS: Fetching from MongoDB for ${conversationId}`);

      const query = {
        conversation_id: conversationId,
        is_deleted: { $ne: true },
        ...(userId ? { deleted_for: { $ne: userId } } : {}),
      };

      if (deletedMsgId !== "0") {
        query.msg_id = { $gt: deletedMsgId };
      }

      const messages = await Message.find(query)
        .sort({ createdAt: -1 })
        .limit(limit)
        .lean();

      if (messages.length === 0) {
        logger.info(`No messages found for ${conversationId}`);
        return [];
      }

      // Step 3: Reverse to get oldest → newest order
      const orderedMessages = messages.reverse();
      const hydratedWithSender = await this.hydrateSenderInfo(orderedMessages);

      // Always hydrate reply preview so payload is consistent between cache-hit and cache-miss paths.
      const hydratedMessages = await this.hydrateReplyPreviews(
        conversationId,
        hydratedWithSender,
        userId,
      );

      // Step 4: Cache the results
      await messageCacheService.addMultipleMessages(
        conversationId,
        hydratedMessages,
      );

      logger.info(`✓ Cached ${orderedMessages.length} messages from MongoDB`);

      return hydratedMessages;
    } catch (error) {
      logger.error("Error getting messages:", error);
      throw error;
    }
  }

  /**
   * Get a focused message window around a target message.
   * Returns oldest -> newest order: [before..., target, after...]
   */
  async getMessageContext(
    conversationId,
    messageId,
    beforeLimit = 20,
    afterLimit = 20,
    userId,
  ) {
    try {
      const targetId = String(messageId || "");
      if (!targetId) return null;

      const targetMessage = await Message.findOne({
        conversation_id: conversationId,
        msg_id: targetId,
        is_deleted: { $ne: true },
        ...(userId ? { deleted_for: { $ne: userId } } : {}),
      }).lean();

      if (!targetMessage || !this.isVisibleToUser(targetMessage, userId)) {
        return null;
      }

      const visibilityFilter = {
        conversation_id: conversationId,
        is_deleted: { $ne: true },
        ...(userId ? { deleted_for: { $ne: userId } } : {}),
      };

      const [beforeMessages, afterMessages] = await Promise.all([
        Message.find({
          ...visibilityFilter,
          msg_id: { $lt: targetId },
        })
          .sort({ msg_id: -1 })
          .limit(Math.max(0, beforeLimit) + 1)
          .lean(),
        Message.find({
          ...visibilityFilter,
          msg_id: { $gt: targetId },
        })
          .sort({ msg_id: 1 })
          .limit(Math.max(0, afterLimit) + 1)
          .lean(),
      ]);

      const hasMoreBefore = beforeMessages.length > Math.max(0, beforeLimit);
      const hasMoreAfter = afterMessages.length > Math.max(0, afterLimit);

      const trimmedBefore = hasMoreBefore
        ? beforeMessages.slice(0, Math.max(0, beforeLimit))
        : beforeMessages;
      const trimmedAfter = hasMoreAfter
        ? afterMessages.slice(0, Math.max(0, afterLimit))
        : afterMessages;

      const orderedMessages = [
        ...trimmedBefore.reverse(),
        targetMessage,
        ...trimmedAfter,
      ];

      const hydratedMessages = await this.hydrateReplyPreviews(
        conversationId,
        orderedMessages,
        userId,
      );

      return {
        targetMessageId: targetId,
        messages: hydratedMessages,
        hasMoreBefore,
        hasMoreAfter,
      };
    } catch (error) {
      logger.error("Error getting message context:", error);
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
  async getOlderMessages(conversationId, beforeMsgId, limit = 20, userId) {
    try {
      logger.info(
        `📥 Loading older messages for ${conversationId}, before msg_id: ${beforeMsgId}`,
      );

      const deletedMsgId = await this.getDeletedMsgId(conversationId, userId);

      // msg_id is stored as string (Snowflake). Keep string comparison to avoid
      // precision loss when converting large IDs to Number.
      const beforeId = String(beforeMsgId);

      const query = {
        conversation_id: conversationId,
        is_deleted: { $ne: true },
        ...(userId ? { deleted_for: { $ne: userId } } : {}),
        msg_id: { $lt: beforeId },
      };

      if (deletedMsgId !== "0") {
        query.msg_id = { $lt: beforeId, $gt: deletedMsgId };
      }

      // Fetch from MongoDB (messages with msg_id < beforeMsgId)
      const messages = await Message.find(query)
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

      const hydratedMessages = await this.hydrateReplyPreviews(
        conversationId,
        orderedMessages,
        userId,
      );

      logger.info(
        `✓ Loaded ${orderedMessages.length} older messages from DB (hasMore: ${hasMore})`,
      );

      // Store hasMore for response
      hydratedMessages._hasMore = hasMore;

      return hydratedMessages;
    } catch (error) {
      logger.error("Error getting older messages:", error);
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
  async getNewerMessages(conversationId, afterMsgId, limit = 20, userId) {
    try {
      logger.info(
        `📤 Loading newer messages for ${conversationId}, after msg_id: ${afterMsgId}`,
      );

      const deletedMsgId = await this.getDeletedMsgId(conversationId, userId);

      // msg_id is stored as string (Snowflake). Keep string comparison to avoid
      // precision loss when converting large IDs to Number.
      let afterId = String(afterMsgId);
      
      if (deletedMsgId !== "0" && BigInt(deletedMsgId) > BigInt(afterId)) {
         afterId = deletedMsgId;
      }

      const messages = await Message.find({
        conversation_id: conversationId,
        is_deleted: { $ne: true },
        ...(userId ? { deleted_for: { $ne: userId } } : {}),
        msg_id: { $gt: afterId },
      })
        .sort({ msg_id: 1 }) // oldest first (smallest ID first)
        .limit(limit)
        .lean();

      if (messages.length === 0) {
        logger.info(`No newer messages found for ${conversationId}`);
        return [];
      }

      logger.info(`✓ Loaded ${messages.length} newer messages from DB`);

      return await this.hydrateReplyPreviews(conversationId, messages, userId);
    } catch (error) {
      logger.error("Error getting newer messages:", error);
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
        { new: true },
      ).lean();

      if (!updated) {
        throw new Error(`Message ${messageId} not found`);
      }

      logger.info(`✏️  Message ${messageId} updated in DB`);

      // Update in Redis cache
      await messageCacheService.updateMessage(
        conversationId,
        messageId,
        updated,
      );

      return updated;
    } catch (error) {
      logger.error("Error updating message:", error);
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
        { new: true },
      ).lean();

      if (!deleted) {
        throw new Error(`Message ${messageId} not found`);
      }

      logger.info(`🗑️  Message ${messageId} deleted (soft delete in DB)`);

      // Remove from Redis cache
      await messageCacheService.removeMessage(conversationId, messageId);

      return deleted;
    } catch (error) {
      logger.error("Error deleting message:", error);
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
        throw new Error("Message not found");
      }

      if (!Array.isArray(message.reactions)) {
        message.reactions = [];
      }

      const normalizedEmoji = String(emoji || "").trim();
      if (!normalizedEmoji) {
        throw new Error("Invalid reaction");
      }

      const currentReactionIndex = message.reactions.findIndex(
        (r) => r.user_id === userId,
      );
      const currentReactionType =
        currentReactionIndex >= 0
          ? String(message.reactions[currentReactionIndex]?.type || "")
          : "";

      if (currentReactionIndex >= 0) {
        if (message.reactions[currentReactionIndex].type === normalizedEmoji) {
          // Tap same emoji again -> remove reaction.
          message.reactions.splice(currentReactionIndex, 1);
        } else {
          // Replace with new emoji.
          message.reactions[currentReactionIndex] = {
            user_id: userId,
            type: normalizedEmoji,
          };
        }
      } else {
        message.reactions.push({ user_id: userId, type: normalizedEmoji });
      }

      // Save to MongoDB
      await message.save();
      const updated = message.toObject();
      const responseMessage = {
        ...updated,
        reactions: Array.isArray(updated.reactions) ? updated.reactions : [],
      };

      logger.info(
        `😊 Reaction ${normalizedEmoji} updated on message ${messageId}`,
      );

      // Update Redis cache
      await messageCacheService.updateMessage(
        conversationId,
        messageId,
        responseMessage,
      );

      return responseMessage;
    } catch (error) {
      logger.error("Error adding reaction:", error);
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
      logger.error("Error getting message count:", error);
      return 0;
    }
  }
}

module.exports = new MessageRepository();
