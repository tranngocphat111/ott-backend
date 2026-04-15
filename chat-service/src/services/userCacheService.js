const redis = require("redis");
const logger = require("../utils/logger");

class UserCacheService {
  constructor() {
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
    this.redisAvailable = true;

    this.client.on("error", (err) => {
      logger.error("User Redis Client Error:", err);

      const message = String(err?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
    });

    this.client.connect().catch((err) => {
      logger.error("Failed to connect User Redis:", err);

      const message = String(err?.message || "");
      if (/NOAUTH|WRONGPASS|AUTH/i.test(message)) {
        this.redisAvailable = false;
      }
    });

    this.USER_KEY_PREFIX = "users:profile:";
    this.CACHE_TTL = Number(process.env.USER_CACHE_TTL || 3600);
  }

  getUserKey(userId) {
    return `${this.USER_KEY_PREFIX}${userId}`;
  }

  async getCachedUser(userId) {
    if (!this.redisAvailable) {
      return null;
    }

    try {
      const key = this.getUserKey(userId);
      const cached = await this.client.get(key);
      if (!cached) return null;
      return JSON.parse(cached);
    } catch (error) {
      logger.error(`Error getting cached user ${userId}:`, error);
      return null;
    }
  }

  async setCachedUser(userId, user) {
    if (!this.redisAvailable) {
      return false;
    }

    try {
      const key = this.getUserKey(userId);
      await this.client.setEx(key, this.CACHE_TTL, JSON.stringify(user));
      return true;
    } catch (error) {
      logger.error(`Error setting cached user ${userId}:`, error);
      return false;
    }
  }

  async clearCachedUser(userId) {
    if (!this.redisAvailable) {
      return false;
    }

    try {
      const key = this.getUserKey(userId);
      await this.client.del(key);
      return true;
    } catch (error) {
      logger.error(`Error clearing cached user ${userId}:`, error);
      return false;
    }
  }
}

module.exports = new UserCacheService();
