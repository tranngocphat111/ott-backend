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

const allowedAudioExtensions = new Set([
  '.webm',
  '.m4a',
  '.mp3',
  '.wav',
  '.ogg',
  '.oga',
  '.aac',
  '.flac',
  '.mp4',
]);

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    cb(null, `voice-${Date.now()}${path.extname(file.originalname)}`);
  },
});

const upload = multer({
  storage,
  limits: {
    files: 1,
    fileSize: Number(process.env.AI_MAX_AUDIO_BYTES || 25 * 1024 * 1024),
  },
  fileFilter: (req, file, cb) => {
    const extension = path.extname(file.originalname || '').toLowerCase();
    const isAudioMime = String(file.mimetype || '').startsWith('audio/');
    const isKnownAudioFile = allowedAudioExtensions.has(extension);

    if (isAudioMime || isKnownAudioFile) {
      cb(null, true);
      return;
    }

    cb(new Error('Chỉ hỗ trợ file âm thanh cho AI transcription.'));
  },
});

const uploadAudio = (req, res, next) => {
  upload.single('audio')(req, res, (error) => {
    if (error) {
      return res.status(400).json({ error: error.message });
    }
    next();
  });
};

router.get('/smart-replies', aiController.getSmartReplies);
router.get('/summarize', aiController.summarizeConversation);
router.post('/translate', aiController.translateText);
router.post('/transcribe', uploadAudio, aiController.transcribeVoice);

module.exports = router;
