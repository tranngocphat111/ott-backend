const mongoose = require("mongoose");

const numberFromEnv = (name, fallback) => {
  const value = Number(process.env[name]);
  return Number.isFinite(value) && value > 0 ? value : fallback;
};

const connectDB = async () => {
  try {
    await mongoose.connect(process.env.MONGO_URI, {
      maxPoolSize: numberFromEnv("MONGO_MAX_POOL_SIZE", 20),
      minPoolSize: numberFromEnv("MONGO_MIN_POOL_SIZE", 1),
      serverSelectionTimeoutMS: numberFromEnv(
        "MONGO_SERVER_SELECTION_TIMEOUT_MS",
        5000,
      ),
      connectTimeoutMS: numberFromEnv("MONGO_CONNECT_TIMEOUT_MS", 10000),
      socketTimeoutMS: numberFromEnv("MONGO_SOCKET_TIMEOUT_MS", 45000),
      maxIdleTimeMS: numberFromEnv("MONGO_MAX_IDLE_TIME_MS", 30000),
    });
    console.log("Ket noi MongoDB thanh cong");
  } catch (error) {
    console.error("Ket noi MongoDB that bai:", error.message);
    process.exit(1);
  }
};

module.exports = connectDB;
