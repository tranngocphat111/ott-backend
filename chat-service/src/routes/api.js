const express = require("express");
const router = express.Router();

const UserController = require("../controllers/userController");
const ConversationController = require("../controllers/conversationController");
const MessageController = require("../controllers/messageController");
const ParticipantController = require("../controllers/participantController");
const UserCategoryController = require("../controllers/userCategoryController");

router.post("/users/sync", UserController.syncUser);
router.get("/users/:userId", UserController.getUser);
router.get("/users", UserController.getAllUsers);

router.post("/conversations", ConversationController.createConversation);
router.post("/conversations/add-member", ConversationController.addMember);
router.put(
  "/conversations/:conversationId",
  ConversationController.updateConversation,
);
router.delete(
  "/conversations/:conversationId/dissolve/:userId",
  ConversationController.dissolveGroup,
);

router.get(
  "/participants/:userId",
  ParticipantController.getConversationsByUserId,
);
router.get(
  "/participants/members/:conversationId",
  ParticipantController.getConversationMembers,
);
router.put(
  "/participants/category",
  ParticipantController.updateConversationCategory,
);
router.put(
  "/participants/notification",
  ParticipantController.updateNotificationStatus,
);
router.put("/participants/pin", ParticipantController.updatePinStatus);
router.put("/participants/read", ParticipantController.updateLastRead);
router.put(
  "/participants/delete-conversation",
  ParticipantController.deleteConversation,
);
router.put(
  "/participants/role/:conversationId/:userId",
  ParticipantController.updateMemberRole,
);
router.put(
  "/participants/nickname/:conversationId/:userId",
  ParticipantController.updateMemberNickname,
);
router.delete(
  "/participants/leave/:conversationId/:userId",
  ParticipantController.leaveGroup,
);
router.delete(
  "/participants/remove/:conversationId/:userId",
  ParticipantController.removeMember,
);

router.post("/messages/presigned-url", MessageController.generatePresignedUrl);
router.post("/messages", MessageController.sendMessage);
router.post("/messages/forward", MessageController.forwardMessage);
router.put("/messages/:msgId/reaction", MessageController.reactToMessage);
router.put("/messages/:msgId/revoke", MessageController.revokeMessage);
router.put("/messages/:msgId/delete", MessageController.deleteMessage);
router.put("/messages/:msgId/pin", MessageController.pinMessage);
router.get("/messages/:conversationId", MessageController.getMessages);
router.get(
  "/messages/:conversationId/pinned",
  MessageController.getPinnedMessages,
);
router.get(
  "/messages/:conversationId/media",
  MessageController.getMediaMessages,
);
router.get(
  "/messages/:conversationId/media-gallery",
  MessageController.getMediaGallery,
);
router.get(
  "/messages/:conversationId/media-around",
  MessageController.getMediaAroundTarget,
);
router.get(
  "/messages/:conversationId/files",
  MessageController.getFileMessages,
);
router.get(
  "/messages/:conversationId/links",
  MessageController.getLinkMessages,
);
router.get("/search/:userId", MessageController.searchEverything);

// User Category routes
router.get("/categories/:userId", UserCategoryController.getUserCategories);
router.post("/categories", UserCategoryController.createCategory);
router.put("/categories/:categoryId", UserCategoryController.updateCategory);
router.delete("/categories/:categoryId", UserCategoryController.deleteCategory);
router.post(
  "/categories/defaults",
  UserCategoryController.createDefaultCategories,
);

module.exports = router;
