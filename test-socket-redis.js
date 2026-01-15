const { io } = require("socket.io-client");

// Đảm bảo Server của bạn đang chạy ở port này (mặc định là 5000)
const URL = "http://localhost:5000";

console.log("🚀 ĐANG KẾT NỐI ĐẾN SERVER...");

// 1. Tạo kết nối cho Người Nhận (Receiver - Client B)
const clientB = io(URL);

// 2. Tạo kết nối cho Người Gửi (Sender - Client A)
const clientA = io(URL);

const TEST_ROOM_ID = "room_redis_test_123";

// --- LOGIC CỦA CLIENT B (Người Nhận) ---
clientB.on("connect", () => {
  console.log(`✅ Client B (Nhận) đã kết nối: ${clientB.id}`);

  // Client B tham gia phòng
  clientB.emit("join_conversation", TEST_ROOM_ID);
});

// Client B lắng nghe sự kiện nhận tin nhắn
clientB.on("receive_message", (data) => {
  console.log("\n------------------------------------------------");
  console.log("🎉 KẾT QUẢ: Client B đã nhận được tin nhắn!");
  console.log("   - Nội dung:", data.content);
  console.log("   - Từ Conversation:", data.conversationId);
  console.log("------------------------------------------------");
  console.log("✅ TEST THÀNH CÔNG! Redis/Socket hoạt động tốt.");

  // Ngắt kết nối để kết thúc test
  clientA.disconnect();
  clientB.disconnect();
  process.exit(0);
});

// --- LOGIC CỦA CLIENT A (Người Gửi) ---
clientA.on("connect", () => {
  console.log(`✅ Client A (Gửi) đã kết nối: ${clientA.id}`);

  // Client A tham gia phòng
  clientA.emit("join_conversation", TEST_ROOM_ID);

  // Đợi 1 giây để đảm bảo cả 2 đã vào phòng ổn định rồi mới gửi
  setTimeout(() => {
    console.log(
      `\n⏳ Client A đang gửi tin nhắn vào phòng: ${TEST_ROOM_ID}...`
    );

    // Giả lập dữ liệu gửi đi (khớp với logic bên server app.js của bạn)
    const payload = {
      conversationId: TEST_ROOM_ID,
      senderId: "user_test_A",
      content: "Hello Redis, có nghe thấy tôi không?",
      type: "text",
    };

    // Bắn sự kiện gửi tin
    clientA.emit("send_message", payload);
  }, 1000);
});
