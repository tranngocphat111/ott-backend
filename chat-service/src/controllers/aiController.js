const aiService = require('../services/aiService');
const messageService = require('../services/messageService');
const path = require('path');
const fs = require('fs');

exports.getSmartReplies = async (req, res) => {
  try {
    const { conversationId } = req.query;
    if (!conversationId) {
      return res.status(400).json({ error: 'conversationId is required' });
    }

    // Lấy 20 tin nhắn gần nhất và lọc chỉ lấy tin nhắn văn bản
    const messages = await messageService.getMessages(conversationId, { limit: 20 });
    const textMessages = (messages || []).filter(m => m.type === 'text');
    
    if (textMessages.length === 0) {
      return res.json([]);
    }

    // Đảo ngược để theo thứ tự thời gian tăng dần cho AI dễ hiểu
    const contextMessages = [...textMessages].reverse().map(m => ({
      senderName: m.sender_name || 'Người dùng',
      content: Array.isArray(m.content) ? m.content[0] : m.content
    }));

    const suggestions = await aiService.generateSmartReplies(contextMessages);
    res.json(suggestions);
  } catch (error) {
    console.error('Smart Reply Controller Error:', error);
    res.status(500).json({ error: error.message });
  }
};

exports.summarizeConversation = async (req, res) => {
  try {
    const { conversationId } = req.query;
    if (!conversationId) {
      return res.status(400).json({ error: 'conversationId is required' });
    }

    // Lấy 20 tin nhắn gần nhất để tóm tắt
    const messages = await messageService.getMessages(conversationId, { limit: 20 });
    
    if (!messages || !Array.isArray(messages) || messages.length === 0) {
      return res.json({ summary: 'Hội thoại chưa có đủ dữ liệu để tóm tắt.' });
    }

    const contextMessages = [...messages].reverse().map(m => ({
      senderName: m.sender_name || 'Người dùng',
      content: Array.isArray(m.content) ? m.content[0] : m.content
    }));

    const summary = await aiService.summarizeChat(contextMessages);
    res.json({ summary });
  } catch (error) {
    console.error('Summarization Controller Error:', error);
    res.status(500).json({ error: error.message });
  }
};

exports.translateText = async (req, res) => {
  try {
    const { text, targetLang } = req.body;
    if (!text) {
      return res.status(400).json({ error: 'text is required' });
    }

    const translatedText = await aiService.translateMessage(text, targetLang);
    res.json({ translatedText });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.transcribeVoice = async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'Audio file is required' });
    }

    const transcription = await aiService.transcribeAudio(req.file.path);
    
    // Xóa file tạm sau khi xử lý xong
    fs.unlink(req.file.path, (err) => {
      if (err) console.error('Error deleting temp file:', err);
    });

    res.json({ text: transcription });
  } catch (error) {
    // Đảm bảo xóa file nếu lỗi xảy ra
    if (req.file) {
      fs.unlink(req.file.path, () => {});
    }
    res.status(500).json({ error: error.message });
  }
};
