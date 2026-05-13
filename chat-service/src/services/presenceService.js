const redis = require("redis");
const User = require("../models/User");
const { publishToQueue } = require("../config/rabbitmq");

// ============================================================
// REDIS CLIENT
// ============================================================
const redisConfig = process.env.REDIS_URL
  ? { url: process.env.REDIS_URL }
  : {
      socket: {
        host: process.env.REDIS_HOST || "localhost",
        port: Number(process.env.REDIS_PORT || 6379),
      },
      password: process.env.REDIS_PASSWORD || undefined,
    };

const presenceRedis = redis.createClient(redisConfig);
presenceRedis.on("error", (err) =>
  console.error("[Presence Redis] Error:", err.message)
);
presenceRedis.connect().catch((err) =>
  console.error("[Presence Redis] Connect error:", err.message)
);

// ============================================================
// CONSTANTS
// ============================================================
// Mỗi presence:{userId} key sẽ tồn tại trong Redis 5 phút
// nếu không được gia hạn (tránh zombie khi server crash)
const PRESENCE_TTL_SECONDS = 300; // 5 phút

// Debounce: Chờ 4 giây trước khi broadcast USER_OFFLINE
// để tránh nhấp nháy khi mạng di động bị gián đoạn ngắn
const OFFLINE_DEBOUNCE_MS = 4000;

// Lưu các timer debounce theo userId (in-memory, chỉ cho single-node)
const offlineTimers = new Map();

// ============================================================
// HELPERS
// ============================================================
const presenceKey = (userId) => `presence:${userId}`;

/**
 * Lấy danh sách socketIds đang active của một user
 */
const getSockets = async (userId) => {
  try {
    return await presenceRedis.sMembers(presenceKey(userId));
  } catch {
    return [];
  }
};

/**
 * Gia hạn TTL cho presence key (gọi mỗi khi nhận được heartbeat thành công)
 */
const refreshTTL = async (userId) => {
  try {
    await presenceRedis.expire(presenceKey(userId), PRESENCE_TTL_SECONDS);
  } catch {
    /* ignore */
  }
};

// ============================================================
// CORE PRESENCE FUNCTIONS
// ============================================================

/**
 * Xử lý khi người dùng kết nối Socket mới.
 * Trả về true nếu đây là session đầu tiên (user vừa online).
 */
const handleConnect = async (userId, socketId) => {
  try {
    const key = presenceKey(userId);

    // Hủy timer debounce nếu đang có (user reconnect)
    if (offlineTimers.has(userId)) {
      clearTimeout(offlineTimers.get(userId));
      offlineTimers.delete(userId);
      console.log(`[Presence] Cancelled offline timer for user ${userId} (reconnected)`);
    }

    // Đọc kích thước Set TRƯỚC khi thêm để biết đây có phải session đầu tiên không
    const prevSize = await presenceRedis.sCard(key);

    // Thêm socketId vào Set và thiết lập TTL
    await presenceRedis.sAdd(key, socketId);
    await presenceRedis.expire(key, PRESENCE_TTL_SECONDS);

    const isFirstSession = prevSize === 0;
    console.log(
      `[Presence] User ${userId} connected (socket: ${socketId}). Total sockets: ${prevSize + 1}. First: ${isFirstSession}`
    );

    return isFirstSession;
  } catch (err) {
    console.error("[Presence] handleConnect error:", err.message);
    return false;
  }
};

/**
 * Xử lý khi một socket của người dùng bị ngắt.
 * Trả về { isFullyOffline, remainingCount }.
 */
const handleDisconnect = async (userId, socketId) => {
  try {
    const key = presenceKey(userId);
    await presenceRedis.sRem(key, socketId);
    const remaining = await presenceRedis.sCard(key);

    console.log(
      `[Presence] User ${userId} disconnected (socket: ${socketId}). Remaining sockets: ${remaining}`
    );

    return { isFullyOffline: remaining === 0, remainingCount: remaining };
  } catch (err) {
    console.error("[Presence] handleDisconnect error:", err.message);
    return { isFullyOffline: true, remainingCount: 0 };
  }
};

/**
 * Kiểm tra xem user hiện tại có đang online không.
 */
const isOnline = async (userId) => {
  try {
    const count = await presenceRedis.sCard(presenceKey(userId));
    return count > 0;
  } catch {
    return false;
  }
};

/**
 * Lấy trạng thái online của nhiều user cùng lúc (dùng pipeline để tối ưu).
 * Trả về Map<userId, { isOnline: boolean, lastSeenAt: Date | null }>.
 */
const getBulkOnlineStatus = async (userIds) => {
  const result = new Map();
  if (!userIds || userIds.length === 0) return result;

  try {
    const pipeline = presenceRedis.multi();
    for (const uid of userIds) {
      pipeline.sCard(presenceKey(uid));
    }
    const counts = await pipeline.exec();
    
    const offlineUserIds = [];
    userIds.forEach((uid, idx) => {
      const isOnline = (counts[idx] || 0) > 0;
      if (isOnline) {
        result.set(uid, { isOnline: true, lastSeenAt: new Date() });
      } else {
        offlineUserIds.push(uid);
      }
    });

    if (offlineUserIds.length > 0) {
      const users = await User.find(
        { user_id: { $in: offlineUserIds } },
        "user_id last_active_at"
      ).lean();
      
      const userMap = new Map(users.map((u) => [u.user_id, u.last_active_at]));
      
      for (const uid of offlineUserIds) {
        result.set(uid, {
          isOnline: false,
          lastSeenAt: userMap.get(uid) || null,
        });
      }
    }
  } catch (err) {
    console.error("[Presence] getBulkOnlineStatus error:", err.message);
    // Fallback: tất cả offline
    userIds.forEach((uid) => result.set(uid, { isOnline: false, lastSeenAt: null }));
  }
  return result;
};

// ============================================================
// BROADCAST HELPERS
// ============================================================

/**
 * Lưu last_seen vào MongoDB và broadcast USER_OFFLINE.
 */
const broadcastOffline = async (io, userId, friendsAndParticipants) => {
  const now = new Date();

  // Cập nhật MongoDB
  try {
    await User.findOneAndUpdate(
      { user_id: userId },
      { is_online: false, last_active_at: now },
      { upsert: false }
    );
    console.log(`[Presence] Updated last_seen for user ${userId}`);
  } catch (err) {
    console.error("[Presence] MongoDB update error:", err.message);
  }

  // Broadcast socket event USER_OFFLINE cho bạn bè/thành viên nhóm
  const payload = {
    userId,
    isOnline: false,
    lastSeenAt: now.toISOString(),
  };

  if (friendsAndParticipants && friendsAndParticipants.length > 0) {
    friendsAndParticipants.forEach((uid) => {
      io.to(`user:${uid}`).emit("trang_thai_hoat_dong", payload);
    });
  }

  // Publish lên RabbitMQ để các service khác biết (notification-service, etc.)
  try {
    await publishToQueue("user.presence", {
      event: "USER_OFFLINE",
      userId,
      lastSeenAt: now.toISOString(),
      timestamp: now.toISOString(),
    });
  } catch {
    /* RabbitMQ optional, không block */
  }

  console.log(`[Presence] Broadcast USER_OFFLINE for user ${userId}`);
};

/**
 * Broadcast USER_ONLINE cho bạn bè/thành viên nhóm.
 */
const broadcastOnline = async (io, userId, friendsAndParticipants) => {
  // Cập nhật MongoDB
  try {
    await User.findOneAndUpdate(
      { user_id: userId },
      { is_online: true, last_active_at: new Date() },
      { upsert: false }
    );
  } catch (err) {
    console.error("[Presence] MongoDB update online error:", err.message);
  }

  const payload = {
    userId,
    isOnline: true,
    lastSeenAt: null,
  };

  if (friendsAndParticipants && friendsAndParticipants.length > 0) {
    friendsAndParticipants.forEach((uid) => {
      io.to(`user:${uid}`).emit("trang_thai_hoat_dong", payload);
    });
  }

  // Publish lên RabbitMQ
  try {
    await publishToQueue("user.presence", {
      event: "USER_ONLINE",
      userId,
      timestamp: new Date().toISOString(),
    });
  } catch {
    /* optional */
  }

  console.log(`[Presence] Broadcast USER_ONLINE for user ${userId}`);
};

/**
 * Đặt debounce timer trước khi broadcast offline.
 * Nếu user reconnect trong khoảng OFFLINE_DEBOUNCE_MS, timer sẽ bị hủy.
 */
const scheduleOfflineBroadcast = (io, userId, getFriendsCallback) => {
  // Hủy timer cũ nếu có
  if (offlineTimers.has(userId)) {
    clearTimeout(offlineTimers.get(userId));
  }

  const timer = setTimeout(async () => {
    offlineTimers.delete(userId);

    // Double-check: Xác nhận lại user vẫn offline trong Redis trước khi broadcast
    const stillOffline = !(await isOnline(userId));
    if (!stillOffline) {
      console.log(`[Presence] User ${userId} reconnected before debounce ended. Skip offline broadcast.`);
      return;
    }

    // Lấy danh sách bạn bè/thành viên nhóm để notify
    let friends = [];
    try {
      friends = await getFriendsCallback(userId);
    } catch {
      /* fallback to empty */
    }

    await broadcastOffline(io, userId, friends);
  }, OFFLINE_DEBOUNCE_MS);

  offlineTimers.set(userId, timer);
};

module.exports = {
  handleConnect,
  handleDisconnect,
  isOnline,
  getBulkOnlineStatus,
  broadcastOnline,
  broadcastOffline,
  scheduleOfflineBroadcast,
  refreshTTL,
  getSockets,
  presenceRedis,
};
