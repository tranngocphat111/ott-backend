const express = require("express");
const router = express.Router();
const relationshipController = require("../controllers/relationshipController");

router.post("/send", relationshipController.sendRequest);
router.post("/accept/:relationshipId", relationshipController.acceptRequest);
router.post("/reject/:relationshipId", relationshipController.rejectRequest);
router.get("/status", relationshipController.getStatus);

module.exports = router;
