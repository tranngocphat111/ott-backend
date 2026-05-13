const express = require('express');
const router = express.Router();
const aiController = require('../controllers/aiController');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

// Cấu hình lưu trữ file tạm cho STT
const uploadDir = path.join(__dirname, '../../temp');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    cb(null, `voice-${Date.now()}${path.extname(file.originalname)}`);
  },
});

const upload = multer({ storage });

router.get('/smart-replies', aiController.getSmartReplies);
router.get('/summarize', aiController.summarizeConversation);
router.post('/translate', aiController.translateText);
router.post('/transcribe', upload.single('audio'), aiController.transcribeVoice);

module.exports = router;
