const mongoose = require("mongoose");
const dotenv = require("dotenv");

dotenv.config();

const connectDB = require("./src/config/db");

const UserService = require("./src/services/userService");
const ConversationService = require("./src/services/conversationService");
const ParticipantService = require("./src/services/participantService");
const MessageService = require("./src/services/messageService");

const runTest = async () => {
  try {
    console.log("🚀 BẮT ĐẦU TEST KỊCH BẢN CHAT...");

    // BƯỚC 1: KẾT NỐI DB
    // Sử dụng hàm connectDB của bạn
    await connectDB();
    console.log("------------------------------------------------");

    // ====================================================
    // SCENARIO 1: SYNC USER (Tạo Alice và Bob)
    // ====================================================
    console.log("1️⃣  Sync Users (Alice & Bob)");

    // Tạo ID giả lập
    const aliceId = "USER_ALICE_" + Date.now();
    const bobId = "USER_BOB_" + Date.now();

    const alice = await UserService.syncUser({
      user_id: aliceId,
      name: "Alice Wonderland",
    });

    const bob = await UserService.syncUser({
      user_id: bobId,
      name: "Bob The Builder",
    });

    console.log(`   ✅ Alice ID: ${alice.user_id}`);
    console.log(`   ✅ Bob ID: ${bob.user_id}`);

    // ====================================================
    // SCENARIO 2: TẠO HỘI THOẠI (Alice tạo nhóm)
    // ====================================================
    console.log("\n2️⃣  Alice tạo nhóm chat");

    // Lưu ý: Logic createConversation hiện tại đã tự thêm Alice vào Participant (nếu bạn đã update code service)
    // Nếu chưa, nó chỉ tạo conversation.
    const conversation = await ConversationService.createConversation({
      creatorId: alice.user_id,
      type: "group",
    });

    console.log(`   ✅ Conversation Created ID: ${conversation._id}`);

    // ====================================================
    // SCENARIO 3: THÊM THÀNH VIÊN (Thêm Bob vào nhóm)
    // ====================================================
    console.log("\n3️⃣  Alice thêm Bob vào nhóm");

    // Đảm bảo Alice đã ở trong nhóm (đề phòng logic create chưa thêm)
    await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: alice.user_id,
      role: "admin",
    });

    // Thêm Bob
    const bobParticipant = await ParticipantService.addParticipant({
      conversationId: conversation._id,
      userId: bob.user_id,
      role: "user",
    });

    console.log(
      `   ✅ Đã thêm Bob vào participant (Role: ${bobParticipant.roles})`
    );

    // ====================================================
    // SCENARIO 4: CHAT (Alice nhắn "Xin chào Bob")
    // ====================================================
    console.log("\n4️⃣  Alice gửi tin nhắn");

    const messageContent = "Xin chào Bob, test Docker Redis!";

    const sentMessage = await MessageService.sendMessage({
      conversationId: conversation._id,
      senderId: alice.user_id,
      content: messageContent,
      type: "text",
    });

    console.log(`   ✅ Message Sent ID: ${sentMessage.msg_id}`);
    console.log(`   ✅ Nội dung: "${sentMessage.content[0]}"`);

    // ====================================================
    // SCENARIO 5: KIỂM TRA KẾT QUẢ (VERIFY)
    // ====================================================
    console.log("\n5️⃣  KIỂM TRA DỮ LIỆU (VERIFY)");
    let allPassed = true;

    // CHECK A: Tin nhắn đã lưu vào DB chưa?
    const history = await MessageService.getMessageHistory(conversation._id);
    const savedMsg = history.find((m) => m.msg_id === sentMessage.msg_id);

    if (savedMsg && savedMsg.content[0] === messageContent) {
      console.log(
        "   [PASS] ✅ Tin nhắn đã được lưu chính xác trong Message Collection."
      );
    } else {
      console.log("   [FAIL] ❌ Không tìm thấy tin nhắn trong DB.");
      allPassed = false;
    }

    // CHECK B: Conversation đã update last_message chưa?
    // Query trực tiếp từ DB để đảm bảo lấy dữ liệu mới nhất
    const updatedConv = await mongoose
      .model("Conversation")
      .findById(conversation._id);

    // Lưu ý: content trong schema Message là mảng, khi lưu vào Conversation nó cũng có thể là mảng
    // tùy vào logic updateLastMessage của bạn. Kiểm tra cả 2 trường hợp.
    const lastMsgContent = Array.isArray(updatedConv.last_message.content)
      ? updatedConv.last_message.content[0]
      : updatedConv.last_message.content;

    if (lastMsgContent === messageContent) {
      console.log(
        "   [PASS] ✅ Conversation đã update last_message chính xác."
      );
    } else {
      console.log("   [FAIL] ❌ Conversation chưa update last_message.");
      console.log("      -> Thực tế trong DB:", lastMsgContent);
      allPassed = false;
    }

    // CHECK C: Bob có trong danh sách Participant không?
    const participants = await ParticipantService.getParticipants(
      conversation._id
    );
    const isBobInGroup = participants.some((p) => p.user_id === bob.user_id);

    if (isBobInGroup) {
      console.log("   [PASS] ✅ Bob đã có mặt trong danh sách Participant.");
    } else {
      console.log("   [FAIL] ❌ Bob không có trong Participant.");
      allPassed = false;
    }

    console.log("------------------------------------------------");
    if (allPassed) {
      console.log("🎉  KẾT LUẬN: HỆ THỐNG CHẠY HOÀN HẢO!  🎉");
    } else {
      console.log("⚠️  KẾT LUẬN: CÓ LỖI XẢY RA, VUI LÒNG KIỂM TRA LẠI.");
    }
  } catch (error) {
    console.error("❌ LỖI FATAL:", error);
  } finally {
    // Đóng kết nối
    await mongoose.connection.close();
    process.exit(0);
  }
};

runTest();
