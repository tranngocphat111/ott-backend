const redis = require("redis");
const logger = require("../utils/logger");

const isEnabled =
  String(process.env.CHAT_CONVERSATION_LIST_CACHE_ENABLED || "true").toLowerCase() !==
  "false";

const positiveInt = (value, fallback) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};

class ConversationListCacheService {
  constructor() {
    this.redisAvailable = isEnabled;
    this.connectPromise = null;
    this.prefix = process.env.CHAT_CONVERSATION_LIST_CACHE_PREFIX || "chat:conversations:";
    this.freshTtlSeconds = positiveInt(
      process.env.CHAT_CONVERSATION_LIST_CACHE_TTL_SECONDS,
      10,
    );
    this.staleTtlSeconds = positiveInt(
      process.env.CHAT_CONVERSATION_LIST_STALE_TTL_SECONDS,
      600,
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
      logger.error("[conversation-list-cache] Redis error:", error);

      const message = String(error?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
    });

    this.connectPromise = this.client.connect().catch((error) => {
      logger.error("[conversation-list-cache] Redis connect failed:", error);

      const message = String(error?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
      this.connectPromise = null;
    });
  }

  getFreshKey(userId) {
    return `${this.prefix}fresh:${userId}`;
  }

  getStaleKey(userId) {
    return `${this.prefix}stale:${userId}`;
  }

  async ensureConnected() {
    if (!this.redisAvailable || !this.client) return false;

    try {
      if (this.client.isOpen) return true;

      if (!this.connectPromise) {
        this.connectPromise = this.client.connect().catch((error) => {
          logger.error("[conversation-list-cache] Redis reconnect failed:", error);
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
      const cached = await this.client.get(key);
      if (!cached) return undefined;
      return JSON.parse(cached);
    } catch (error) {
      logger.warn("[conversation-list-cache] get failed:", error?.message || error);
      return undefined;
    }
  }

  async getFresh(userId) {
    if (!userId) return undefined;
    return this.getJson(this.getFreshKey(userId));
  }

  async getStale(userId) {
    if (!userId) return undefined;
    return this.getJson(this.getStaleKey(userId));
  }

  async set(userId, conversations) {
    if (!userId || conversations == null || !(await this.ensureConnected())) {
      return false;
    }

    try {
      const payload = JSON.stringify(conversations);
      await this.client
        .multi()
        .setEx(this.getFreshKey(userId), this.freshTtlSeconds, payload)
        .setEx(this.getStaleKey(userId), this.staleTtlSeconds, payload)
        .exec();
      return true;
    } catch (error) {
      logger.warn("[conversation-list-cache] set failed:", error?.message || error);
      return false;
    }
  }
}

module.exports = new ConversationListCacheService();
