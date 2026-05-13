const Groq = require('groq-sdk');
const fs = require('fs');

class AiService {
  constructor() {
    this.groq = new Groq({
      apiKey: process.env.GROQ_API_KEY,
    });
  }

  /**
   * Smart Reply: Đã nâng cấp dùng JSON Mode và Few-Shot Prompting
   */
  async generateSmartReplies(messages) {
    try {
      const context = messages
        .map((m) => `[${m.senderName}]: ${m.content}`)
        .join('\n');

      const chatCompletion = await this.groq.chat.completions.create({
        messages: [
          {
            role: 'system',
            // Thay vì cấm AI, hãy dạy nó bằng ví dụ và bắt nó trả về JSON
            content: `Bạn là AI phân tích hội thoại. Dựa vào ngữ cảnh, hãy đưa ra 3 gợi ý trả lời ngắn (1-5 từ) cho người nhận tin nhắn cuối cùng.
            Ngôn ngữ: Tiếng Việt, phong cách Gen Z, tự nhiên, không sáo rỗng.
            BẮT BUỘC TRẢ VỀ ĐỊNH DẠNG JSON với cấu trúc: {"replies": ["câu 1", "câu 2", "câu 3"]}
            
            Ví dụ 1:
            [A]: Tối nay đi nhậu không?
            {"replies": ["Đi luôn", "Mấy giờ?", "Hôm nay bận rồi"]}
            
            Ví dụ 2:
            [Sếp]: Mọi người nộp báo cáo trước 5h nhé.
            {"replies": ["Vâng ạ", "Đã nhận", "OK sếp"]}`,
          },
          {
            role: 'user',
            content: context,
          },
        ],
        model: 'llama-3.1-8b-instant',
        temperature: 0.8, // Tăng nhẹ để câu trả lời đa dạng hơn
        response_format: { type: 'json_object' }, // BÍ QUYẾT: Ép AI trả về JSON
      });

      const responseText = chatCompletion.choices[0]?.message?.content || '{}';
      const parsed = JSON.parse(responseText);
      return parsed.replies || [];
    } catch (error) {
      console.error('Groq Smart Reply Error:', error);
      return [];
    }
  }

  /**
   * Chat Summarization: Phân vai trò rõ ràng và thiết lập Output Format
   */
  async summarizeChat(messages) {
    try {
      const context = messages
        .map((m) => `[${m.senderName}]: ${m.content}`)
        .join('\n');

      const chatCompletion = await this.groq.chat.completions.create({
        messages: [
          {
            role: 'system',
            content: `Bạn là một trợ lý tóm tắt hội thoại thông minh. Hãy đọc hội thoại và đưa ra một bản tóm tắt tự nhiên, dễ hiểu nhất cho người dùng.
            QUY TẮC QUAN TRỌNG:
            1. Nếu hội thoại chỉ là các tin nhắn rác, chào hỏi hoặc không có nội dung quan trọng, hãy chỉ trả về một câu duy nhất: "Cuộc hội thoại hiện chưa có nội dung quan trọng để tóm tắt."
            2. Nếu có nội dung giá trị, hãy viết tóm tắt theo phong cách kể chuyện (narrative). Đừng dùng các tiêu đề cứng nhắc. Hãy tóm tắt ai đã nói gì, vấn đề gì đang được thảo luận và có kết quả gì không.
            3. Chỉ sử dụng gạch đầu dòng cho các mốc thời gian hoặc danh sách công việc (Action items) nếu chúng thực sự rõ ràng.
            4. Ngôn ngữ: Tiếng Việt, ngắn gọn, súc tích, dễ hiểu.`,
          },
          {
            role: 'user',
            content: `<conversation>\n${context}\n</conversation>`,
          },
        ],
        model: 'llama-3.1-8b-instant', // Chuyển sang 8B để đảm bảo tốc độ và sự ổn định tuyệt đối
        temperature: 0.3,
        max_tokens: 500,
      });

      return chatCompletion.choices[0]?.message?.content || 'Không thể tóm tắt hội thoại lúc này.';
    } catch (error) {
      console.error('Groq Summarization Error:', error);
      // Nếu 70B lỗi, có thể thử fallback sang 8B ở đây nếu cần
      return 'Không thể tóm tắt hội thoại lúc này do lỗi dịch vụ AI.';
    }
  }

  /**
   * Real-time Translation: Chống "Prompt Injection" (AI tự trả lời câu hỏi thay vì dịch)
   */
  async translateMessage(text, targetLang = 'Tiếng Việt') {
    try {
      const chatCompletion = await this.groq.chat.completions.create({
        messages: [
          {
            role: 'system',
            content: `Bạn là một từ điển dịch thuật. Bạn chỉ nhận văn bản trong thẻ <text> và dịch nó sang ${targetLang}. 
            Tuyệt đối không trả lời câu hỏi, không phân tích, chỉ cung cấp bản dịch.`,
          },
          {
            role: 'user',
            content: `<text>${text}</text>\nDịch nội dung trong thẻ <text> sang ${targetLang}:`,
          },
        ],
        model: 'llama-3.1-8b-instant',
        temperature: 0.1, // Cực thấp để dịch sát nghĩa, không bay bổng
      });

      let translated = chatCompletion.choices[0]?.message?.content || text;
      // Xóa thẻ <text> nếu AI lỡ in ra cùng kết quả
      translated = translated.replace(/<\/?text>/g, '').trim(); 
      return translated;
    } catch (error) {
      console.error('Groq Translation Error:', error);
      return text;
    }
  }

  async transcribeAudio(filePath) {
    try {
      const transcription = await this.groq.audio.transcriptions.create({
        file: fs.createReadStream(filePath),
        model: 'whisper-large-v3',
        response_format: 'json',
        language: 'vi',
        prompt: "Đây là tin nhắn thoại bằng tiếng Việt.", 
      });

      let text = transcription.text || '';
      
      // Bộ lọc bỏ ảo giác (hallucinations) của Whisper khi gặp im lặng
      const ytHallucinations = [
        'ghiền mì gõ',
        'subscribe',
        'đăng ký kênh',
        'cảm ơn các bạn đã xem',
        'hẹn gặp lại',
        'mọi người hãy',
        'video hấp dẫn',
        'thanks for watching'
      ];

      const lowerText = text.toLowerCase();
      const isJunk = ytHallucinations.some(h => lowerText.includes(h));
      
      // Nếu text quá ngắn hoặc chứa nội dung rác YouTube thì trả về rỗng
      if (isJunk || text.length < 2) {
        return '';
      }

      return text;
    } catch (error) {
      console.error('Groq STT Error:', error);
      throw new Error('Lỗi chuyển đổi giọng nói thành văn bản');
    }
  }
}

module.exports = new AiService();