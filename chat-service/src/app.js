const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const dotenv = require("dotenv");
const cors = require("cors");
const connectDB = require("./config/db");
const apiRoutes = require("./routes/api");

dotenv.config();
connectDB();

const app = express();
const server = http.createServer(app);

app.use(cors());
app.use(express.json());

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
});

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

   socket.on("bat_dau_goi", ({ conversationId, callerId, offer }) => {
     socket
       .to(conversationId)
       .emit("cuoc_goi_den", { conversationId, callerId, offer });
   });

   socket.on("tra_loi_goi", ({ conversationId, answer }) => {
     socket.to(conversationId).emit("chap_nhan_goi", { answer });
   });

   socket.on("trao_doi_duong_truyen", ({ conversationId, candidate }) => {
     socket.to(conversationId).emit("nhan_duong_truyen", { candidate });
   });

   socket.on("ket_thuc_goi", ({ conversationId }) => {
     socket.to(conversationId).emit("ket_thuc_phong_goi");
     console.log(`Cuoc goi ket thuc tai phong ${conversationId}`);
   });

  socket.on("disconnect", () => {
    console.log("User ngat ket noi");
  });
});

app.use("/api", apiRoutes);

app.get("/", (req, res) => res.send("Chat Service dang chay..."));

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Chat Service dang chay tren port ${PORT}`);
});
