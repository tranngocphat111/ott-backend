const redis = require("redis");
const logger = require("../utils/logger");

class UserCacheService {
  constructor() {
    this.client = redis.createClient({
      host: process.env.REDIS_HOST || "localhost",
      port: process.env.REDIS_PORT || 6379,
      password: process.env.REDIS_PASSWORD,
      db: 0,
    });

    this.client.on("error", (err) => {
      logger.error("User Redis Client Error:", err);
    });

    this.client.connect().catch((err) => {
      logger.error("Failed to connect User Redis:", err);
    });

    this.USER_KEY_PREFIX = "users:profile:";
    this.CACHE_TTL = Number(process.env.USER_CACHE_TTL || 3600);
  }

  getUserKey(userId) {
    return `${this.USER_KEY_PREFIX}${userId}`;
  }

  async getCachedUser(userId) {
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
