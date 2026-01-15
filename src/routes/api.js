const express = require("express");
const router = express.Router();

const UserController = require("../controllers/userController");
const ConversationController = require("../controllers/conversationController");
const MessageController = require("../controllers/messageController");

router.post("/users/sync", UserController.syncUser);

router.post("/conversations", ConversationController.createConversation);
router.post("/conversations/add-member", ConversationController.addMember);

router.post("/messages", MessageController.sendMessage);

module.exports = router;
