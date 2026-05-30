const redis = require("redis");
const logger = require("../utils/logger");

const isEnabled =
  String(process.env.CHAT_SEND_REDIS_CACHE_ENABLED || "true").toLowerCase() !==
  "false";

class ChatSendCacheService {
  constructor() {
    this.redisAvailable = isEnabled;
    this.connectPromise = null;
    this.prefix = process.env.CHAT_SEND_REDIS_CACHE_PREFIX || "chat:send:";
    this.defaultTtlSeconds = Math.max(
      1,
      Number(process.env.CHAT_SEND_REDIS_CACHE_TTL_SECONDS || 60),
    );

    if (!isEnabled) {
      this.client = null;
      return;
    }

    const redisUrl = process.env.REDIS_URL || process.env.REDIS_URI;
    const redisConfig = redisUrl
      ? { url: redisUrl }
      : {
          socket: {
            host: process.env.REDIS_HOST || "localhost",
            port: Number(process.env.REDIS_PORT || 6379),
          },
          username: process.env.REDIS_USERNAME || undefined,
          password: process.env.REDIS_PASSWORD || undefined,
          database: Number(process.env.REDIS_DB || 0),
        };

    this.client = redis.createClient(redisConfig);

    this.client.on("error", (error) => {
      logger.error("[chat-send-cache] Redis error:", error);

      const message = String(error?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
    });

    this.connectPromise = this.client.connect().catch((error) => {
      logger.error("[chat-send-cache] Redis connect failed:", error);

      const message = String(error?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
      this.connectPromise = null;
    });
  }

  getKey(key) {
    return `${this.prefix}${key}`;
  }

  async ensureConnected() {
    if (!this.redisAvailable || !this.client) return false;

    try {
      if (this.client.isOpen) return true;

      if (!this.connectPromise) {
        this.connectPromise = this.client.connect().catch((error) => {
          logger.error("[chat-send-cache] Redis reconnect failed:", error);
          this.connectPromise = null;
          throw error;
        });
      }

      await this.connectPromise;
      return this.client.isOpen;
    } catch {
      return false;
    }
  }

  async getJson(key) {
    if (!(await this.ensureConnected())) return undefined;

    try {
      const cached = await this.client.get(this.getKey(key));
      if (!cached) return undefined;
      return JSON.parse(cached);
    } catch (error) {
      logger.warn("[chat-send-cache] get failed:", error?.message || error);
      return undefined;
    }
  }

  async setJson(key, value, ttlSeconds = this.defaultTtlSeconds) {
    if (value == null || !(await this.ensureConnected())) return false;

    try {
      await this.client.setEx(
        this.getKey(key),
        Math.max(1, Number(ttlSeconds || this.defaultTtlSeconds)),
        JSON.stringify(value),
      );
      return true;
    } catch (error) {
      logger.warn("[chat-send-cache] set failed:", error?.message || error);
      return false;
    }
  }
}

module.exports = new ChatSendCacheService();
