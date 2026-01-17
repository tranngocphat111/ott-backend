const express = require("express");
const router = express.Router();

const UserController = require("../controllers/userController");
const ConversationController = require("../controllers/conversationController");
const MessageController = require("../controllers/messageController");

router.post("/users/sync", UserController.syncUser);
router.get("/users", UserController.getAllUsers); 

router.post("/conversations", ConversationController.createConversation);
router.post("/conversations/add-member", ConversationController.addMember);
router.post("/conversations/access", ConversationController.accessConversation);

router.post("/messages", MessageController.sendMessage);
router.get("/messages/:conversationId", MessageController.getMessages); 

module.exports = router;
