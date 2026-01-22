const express = require("express");
const router = express.Router();

const UserController = require("../controllers/userController");
const ConversationController = require("../controllers/conversationController");
const MessageController = require("../controllers/messageController");
const ParticipantController = require("../controllers/participantController");
const UserCategoryController = require("../controllers/userCategoryController");

router.post("/users/sync", UserController.syncUser);
router.get("/users", UserController.getAllUsers);

router.post("/conversations", ConversationController.createConversation);
router.post("/conversations/add-member", ConversationController.addMember);

router.get(
  "/participants/:userId",
  ParticipantController.getConversationsByUserId,
);
router.put("/participants/category", ParticipantController.updateConversationCategory);
router.put("/participants/notification", ParticipantController.updateNotificationStatus);
router.put("/participants/pin", ParticipantController.updatePinStatus);

router.post("/messages/presigned-url", MessageController.generatePresignedUrl);
router.post("/messages", MessageController.sendMessage);
router.get("/messages/:conversationId", MessageController.getMessages);

// User Category routes
router.get("/categories/:userId", UserCategoryController.getUserCategories);
router.post("/categories", UserCategoryController.createCategory);
router.put("/categories/:categoryId", UserCategoryController.updateCategory);
router.delete("/categories/:categoryId", UserCategoryController.deleteCategory);
router.post("/categories/defaults", UserCategoryController.createDefaultCategories);

module.exports = router;
