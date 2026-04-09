const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const dotenv = require("dotenv");
const cors = require("cors");
const connectDB = require("./config/db");
const apiRoutes = require("./routes/api");
const messageRoutes = require("./routes/messageRoutes");
const messageEventsHandler = require("./events/messageEvents");
const ParticipantService = require("./services/participantService");

dotenv.config();
connectDB();

const app = express();
const server = http.createServer(app);

app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ limit: "10mb", extended: true }));

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
});

// ========== MESSAGE EVENTS HANDLER ==========
messageEventsHandler(io);
// ==========================================

// In-memory call state by conversationId.
// Note: This is suitable for single-node deployments.
const activeCalls = new Map();

const normalizeCallType = (callType) => {
  return callType === "voice" ? "voice" : "video";
};

const ensureCallState = (conversationId, callType = "video") => {
  if (!activeCalls.has(conversationId)) {
    activeCalls.set(conversationId, {
      callType: normalizeCallType(callType),
      participants: new Set(),
      startedAt: new Date().toISOString(),
    });
  }

  return activeCalls.get(conversationId);
};

const removeUserFromAllCalls = (userId) => {
  if (!userId) return;

  for (const [conversationId, callState] of activeCalls.entries()) {
    if (!callState.participants.has(userId)) {
      continue;
    }

    callState.participants.delete(userId);

    io.to(`call:${conversationId}`).emit("nguoi_dung_roi_goi", {
      conversationId,
      userId,
      participants: Array.from(callState.participants),
    });

    if (callState.participants.size === 0) {
      activeCalls.delete(conversationId);
    }
  }
};

app.use((req, res, next) => {
  req.io = io;
  next();
});

io.on("connection", (socket) => {
  console.log("User moi vua ket noi:", socket.id);

  socket.on("tham_gia_nhom", (conversationId) => {
    socket.join(conversationId);
    console.log(`User tham gia vao phong: ${conversationId}`);
  });

  // Mỗi user join 1 room riêng theo userId — dùng để nhận tin nhắn và hội thoại mới
  socket.on("tham_gia_user_room", (userId) => {
    socket.data.userId = userId;
    socket.join(`user:${userId}`);
    console.log(`User ${userId} da vao phong ca nhan`);
  });

  socket.on("bat_dau_goi", async ({ conversationId, callerId, callType }) => {
    try {
      if (!conversationId || !callerId) return;

      socket.data.userId = callerId;
      socket.join(`user:${callerId}`);

      const callState = ensureCallState(conversationId, callType);
      callState.participants.add(callerId);

      socket.join(`call:${conversationId}`);

      io.to(`call:${conversationId}`).emit("nguoi_dung_tham_gia_goi", {
        conversationId,
        userId: callerId,
        callType: callState.callType,
        participants: Array.from(callState.participants),
      });

      const participants =
        await ParticipantService.getParticipants(conversationId);
      const targetUserIds = participants
        .map((p) => p.user_id)
        .filter((userId) => userId && userId !== callerId);

      targetUserIds.forEach((userId) => {
        io.to(`user:${userId}`).emit("cuoc_goi_den", {
          conversationId,
          callerId,
          callType: callState.callType,
          startedAt: callState.startedAt,
        });
      });

      io.to(`user:${callerId}`).emit("bat_dau_goi_thanh_cong", {
        conversationId,
        callType: callState.callType,
      });
    } catch (error) {
      console.error("Loi bat_dau_goi:", error.message);
    }
  });

  socket.on("tham_gia_cuoc_goi", ({ conversationId, userId, callType }) => {
    if (!conversationId || !userId) return;

    socket.data.userId = userId;
    socket.join(`user:${userId}`);

    const callState = ensureCallState(conversationId, callType);
    callState.participants.add(userId);

    socket.join(`call:${conversationId}`);

    io.to(`call:${conversationId}`).emit("nguoi_dung_tham_gia_goi", {
      conversationId,
      userId,
      callType: callState.callType,
      participants: Array.from(callState.participants),
    });
  });

  socket.on(
    "gui_offer",
    ({ conversationId, fromUserId, targetUserId, offer, callType }) => {
      if (!conversationId || !fromUserId || !targetUserId || !offer) return;

      io.to(`user:${targetUserId}`).emit("nhan_offer", {
        conversationId,
        fromUserId,
        offer,
        callType: normalizeCallType(callType),
      });
    },
  );

  socket.on(
    "gui_answer",
    ({ conversationId, fromUserId, targetUserId, answer }) => {
      if (!conversationId || !fromUserId || !targetUserId || !answer) return;

      io.to(`user:${targetUserId}`).emit("nhan_answer", {
        conversationId,
        fromUserId,
        answer,
      });
    },
  );

  socket.on(
    "gui_ice_candidate",
    ({ conversationId, fromUserId, targetUserId, candidate }) => {
      if (!conversationId || !fromUserId || !targetUserId || !candidate) return;

      io.to(`user:${targetUserId}`).emit("nhan_ice_candidate", {
        conversationId,
        fromUserId,
        candidate,
      });
    },
  );

  socket.on("roi_cuoc_goi", ({ conversationId, userId }) => {
    if (!conversationId || !userId) return;

    const callState = activeCalls.get(conversationId);
    if (!callState) return;

    callState.participants.delete(userId);
    socket.leave(`call:${conversationId}`);

    io.to(`call:${conversationId}`).emit("nguoi_dung_roi_goi", {
      conversationId,
      userId,
      participants: Array.from(callState.participants),
    });

    if (callState.participants.size === 0) {
      activeCalls.delete(conversationId);
    }
  });

  socket.on("tu_choi_goi", ({ conversationId, userId, callerId }) => {
    if (!conversationId || !userId || !callerId) return;

    io.to(`user:${callerId}`).emit("nguoi_dung_tu_choi_goi", {
      conversationId,
      userId,
    });
  });

  socket.on("ket_thuc_goi", ({ conversationId, userId }) => {
    if (!conversationId) return;

    const callState = activeCalls.get(conversationId);
    if (!callState) return;

    io.to(`call:${conversationId}`).emit("ket_thuc_phong_goi", {
      conversationId,
      endedBy: userId || null,
    });

    for (const participantId of callState.participants) {
      io.to(`user:${participantId}`).emit("ket_thuc_phong_goi", {
        conversationId,
        endedBy: userId || null,
      });
    }

    io.in(`call:${conversationId}`).socketsLeave(`call:${conversationId}`);
    activeCalls.delete(conversationId);
    console.log(`Cuoc goi ket thuc tai phong ${conversationId}`);
  });

  socket.on("disconnect", () => {
    removeUserFromAllCalls(socket.data.userId);
    console.log("User ngat ket noi");
  });
});

// ========== MESSAGE ROUTES ==========
app.use("/api", messageRoutes);

// ========== OTHER ROUTES ==========
app.use("/api", apiRoutes);

app.get("/", (req, res) => res.send("Chat Service dang chay..."));

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Chat Service dang chay tren port ${PORT}`);
});
