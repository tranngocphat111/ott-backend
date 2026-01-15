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

const { createClient } = require("redis");
const { createAdapter } = require("@socket.io/redis-adapter");

app.use(cors());
app.use(express.json());

const pubClient = createClient({
  url: process.env.REDIS_URI,
});
const subClient = pubClient.duplicate();

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"],
  },
});

Promise.all([pubClient.connect(), subClient.connect()]).then(() => {
  io.adapter(createAdapter(pubClient, subClient));
  console.log("Redis da ket noi");
});

io.on("connection", (socket) => {
  console.log("User moi vua ket noi:", socket.id);

  socket.on("join_conversation", (conversationId) => {
    socket.join(conversationId);
    console.log(`User tham gia vao phong: ${conversationId}`);
  });

  socket.on("send_message", (data) => {
    socket.to(data.conversationId).emit("receive_message", data);
  });

  socket.on("disconnect", () => {
    console.log("User ngat ket noi");
  });
});

app.use((req, res, next) => {
  req.io = io;
  next();
});

app.use("/api", apiRoutes);

app.get("/", (req, res) => res.send("Chat Service dang chay..."));

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Chat Service dang chay tren port ${PORT}`);
});
