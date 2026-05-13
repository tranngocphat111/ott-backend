const express = require("express");
const router = express.Router();
const relationshipController = require("../controllers/relationshipController");

router.post("/send", relationshipController.sendRequest);
router.post("/accept/:relationshipId", relationshipController.acceptRequest);
router.post("/reject/:relationshipId", relationshipController.rejectRequest);
router.post("/cancel/:relationshipId", relationshipController.cancelRequest);
router.get("/status", relationshipController.getStatus);
router.get("/:userId/friends", relationshipController.getFriends);
router.post("/unfriend", relationshipController.unfriend);
router.post("/block", relationshipController.blockUser);
router.post("/unblock", relationshipController.unblockUser);

module.exports = router;
