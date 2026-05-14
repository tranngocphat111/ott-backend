/**
 * Socket.IO Message Events Handler
 * Handles real-time message operations
 */

const messageRepository = require("../repositories/messageRepository");
const MessageService = require("../services/messageService");
const logger = require("../utils/logger");

module.exports = (io) => {
  io.on("connection", (socket) => {
    /**
     * Load message history
     */
    socket.on("tai_lich_su_tin_nhan", async (data) => {
      try {
        const { conversationId } = data;

        if (!conversationId) {
          socket.emit("loi_tai_tin_nhan", {
            error: "conversationId is required",
          });
          return;
        }

        logger.info(
          `📥 Loading message history for ${conversationId} from socket`,
        );

        const messages = await messageRepository.getConversationMessages(
          conversationId,
          20,
        );

        socket.emit("lich_su_tin_nhan_da_tai", {
          success: true,
          conversationId,
          messageCount: messages.length,
          messages,
          timestamp: Date.now(),
        });

        logger.info(`✓ History loaded: ${messages.length} messages`);
      } catch (error) {
        logger.error("Error loading message history:", error);
        socket.emit("loi_tai_tin_nhan", {
          error: error.message,
        });
      }
    });

    socket.on("nguoi_dung_dang_soan_tin_nhan", (data) => {
      try {
        const { conversationId, userId } = data || {};

        if (!conversationId || !userId) return;

        socket.to(conversationId).emit("nguoi_dung_dang_soan_tin_nhan", {
          conversationId,
          userId,
          timestamp: Date.now(),
        });
      } catch (error) {
        logger.error("Error broadcasting typing start:", error);
      }
    });

    socket.on("nguoi_dung_ngung_soan_tin_nhan", (data) => {
      try {
        const { conversationId, userId } = data || {};

        if (!conversationId || !userId) return;

        socket.to(conversationId).emit("nguoi_dung_ngung_soan_tin_nhan", {
          conversationId,
          userId,
          timestamp: Date.now(),
        });
      } catch (error) {
        logger.error("Error broadcasting typing stop:", error);
      }
    });

    /**
     * Send new message
     */
    socket.on("gui_tin_nhan", async (data) => {
      try {
        const {
          conversationId,
          userId,
          text,
          type = "text",
          attachment,
        } = data;

        if (!conversationId || !userId || !text) {
          socket.emit("loi_gui_tin_nhan", {
            error: "conversationId, userId, and text are required",
          });
          return;
        }

        logger.info(
          `📝 New message from ${userId} in ${conversationId}: "${text.substring(0, 50)}"`,
        );

        const message = await messageRepository.create(conversationId, userId, {
          text,
          type,
          attachment,
        });

        io.to(conversationId).emit("nhan_tin_nhan_moi", {
          success: true,
          message,
          timestamp: Date.now(),
        });

        logger.info(`✓ Message broadcasted: ${message._id}`);
      } catch (error) {
        logger.error("Error sending message:", error);
        socket.emit("loi_gui_tin_nhan", {
          error: error.message,
        });
      }
    });

    /**
     * Edit message
     */
    socket.on("chinh_sua_tin_nhan", async (data) => {
      try {
        const { conversationId, messageId, text } = data;

        if (!conversationId || !messageId || !text) {
          socket.emit("loi_chinh_sua_tin_nhan", {
            error: "conversationId, messageId, and text are required",
          });
          return;
        }

        logger.info(`✏️  Editing message ${messageId}`);

        const updated = await messageRepository.updateMessage(
          messageId,
          conversationId,
          { text },
        );

        io.to(conversationId).emit("tin_nhan_da_chinh_sua", {
          success: true,
          messageId,
          text: updated.text,
          editedAt: updated.editedAt,
          isEdited: updated.isEdited,
        });

        logger.info(`✓ Edit broadcasted: ${messageId}`);
      } catch (error) {
        logger.error("Error editing message:", error);
        socket.emit("loi_chinh_sua_tin_nhan", {
          error: error.message,
        });
      }
    });

    /**
     * Delete message
     */
    socket.on("xoa_tin_nhan", async (data) => {
      try {
        const { conversationId, messageId, userId } = data;
        const actorId = userId || socket.data.userId;

        if (!conversationId || !messageId || !actorId) {
          socket.emit("loi_xoa_tin_nhan", {
            error: "conversationId, messageId, and userId are required",
          });
          return;
        }

        logger.info(`🗑️  Deleting message ${messageId} for user ${actorId}`);

        const deleted = await MessageService.deleteMessage({
          conversationId,
          msgId: messageId,
          userId: actorId,
        });

        io.to(`user:${actorId}`).emit("tin_nhan_da_xoa", deleted);

        logger.info(`✓ Delete broadcasted: ${messageId}`);
      } catch (error) {
        logger.error("Error deleting message:", error);
        socket.emit("loi_xoa_tin_nhan", {
          error: error.message,
        });
      }
    });

    /**
     * Add reaction
     */
    socket.on("them_reaction", async (data) => {
      try {
        const { conversationId, messageId, userId, emoji } = data;

        if (!conversationId || !messageId || !userId || !emoji) {
          socket.emit("loi_them_reaction", {
            error: "conversationId, messageId, userId, and emoji are required",
          });
          return;
        }

        logger.info(
          `😊 Adding reaction ${emoji} to message ${messageId} by ${userId}`,
        );

        const updated = await messageRepository.addReaction(
          messageId,
          conversationId,
          userId,
          emoji,
        );

        io.to(conversationId).emit("reaction_da_them", {
          success: true,
          messageId,
          emoji,
          reactions: updated.reactions,
        });

        logger.info(`✓ Reaction broadcasted: ${messageId} ${emoji}`);
      } catch (error) {
        logger.error("Error adding reaction:", error);
        socket.emit("loi_them_reaction", {
          error: error.message,
        });
      }
    });
  });
};
