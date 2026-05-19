const User = require("../models/User");
const userService = require("../services/userService");
const ParticipantService = require("../services/participantService");
const relationshipService = require("../services/relationshipService");
const { publishNotification } = require("../events/notificationEvents");

const EXCHANGE_NAME = "user.events";
const QUEUE_CREATED = "chat_service_user_created";
const QUEUE_UPDATED = "chat_service_user_updated";
const QUEUE_LOGOUT = "chat_service_user_logout";
const ROUTING_KEY_CREATED = "user.created";
const ROUTING_KEY_UPDATED = "user.updated";
const ROUTING_KEY_LOGOUT = "user.logout";

const handleUserEvent = async (channel, msg) => {
  if (!msg) return;

  try {
    const routingKey = msg.fields.routingKey;
    const content = JSON.parse(msg.content.toString());
    console.log(` [x] UserConsumer: Received ${routingKey} event:`, content);

    const { userId } = content;

    if (!userId) {
      console.warn(" [!] UserConsumer: Skipping event due to missing userId");
      return channel.ack(msg);
    }

    if (routingKey === "user.created") {
      await userService.createUser(content);
    } else if (routingKey === "user.updated") {
      await userService.updateUser(content);
    }

    console.log(` [v] UserConsumer: Processed ${routingKey} for ${userId}`);
    channel.ack(msg);
  } catch (err) {
    console.error(" [!] UserConsumer: Error processing message:", err.message);
    channel.nack(msg, false, true);
  }
};

const handleUserUpdated = async (channel, msg, io) => {
  if (!msg) return;

  try {
    const content = JSON.parse(msg.content.toString());
    console.log(" [x] UserConsumer: Received user.updated event:", content);

    const { userId } = content;

    if (!userId) {
      console.warn(" [!] UserConsumer: Skipping event due to missing userId");
      return channel.ack(msg);
    }

    // Update user info in DB
    const updatedUser = await userService.updateUserInfo(content);
    console.log(` [v] UserConsumer: Processed user update for ${userId}`);

    // Notify friends
    try {
      const friends = await relationshipService.getFriends(userId);
      friends.forEach(friend => {
        publishNotification({
          recipientId: friend.user_id,
          senderId: userId,
          type: "PROFILE_UPDATE",
          content: `${content.fullName || "Bạn bè của bạn"} đã cập nhật thông tin cá nhân.`,
          referenceId: userId
        });
      });
    } catch (e) {
      console.error("[UserConsumer] Failed to notify friends about profile update:", e);
    }

    // Broadcast to the user's personal room so their clients update info
    if (io) {
      io.to(`user:${userId}`).emit("cap_nhat_thong_tin_ca_nhan", {
        userId: content.userId,
        fullName: content.fullName,
        avatarUrl: content.avatarUrl,
        coverUrl: content.coverUrl,
        bio: content.bio,
        work: content.work,
        location: content.location,
        relationshipStatus: content.relationshipStatus,
        email: content.email,
        phone: content.phone,
      });

      // Broadcast to all active users so they can see the new avatar/name in realtime
      // Since chat-service holds the socket, we emit to a global event or to their conversations.
      // For simplicity, broadcast to everyone or let the frontend re-fetch when needed.
      // Emitting globally allows anyone who is currently viewing the user to update:
      io.emit("cap_nhat_thong_tin_ca_nhan", {
        userId: content.userId,
        fullName: content.fullName,
        avatarUrl: content.avatarUrl,
        coverUrl: content.coverUrl,
        bio: content.bio,
        work: content.work,
        location: content.location,
        relationshipStatus: content.relationshipStatus,
        email: content.email,
        phone: content.phone,
      });
    }

    channel.ack(msg);
  } catch (err) {
    console.error(
      " [!] UserConsumer: Error processing user.updated message:",
      err.message,
    );
    channel.nack(msg, false, false);
  }
};

const handleUserLogout = async (channel, msg, io) => {
  if (!msg) return;

  try {
    const content = JSON.parse(msg.content.toString());
    console.log(" [x] UserConsumer: Received user.logout event:", content);

    const { userId, action, deviceId, revokedDeviceIds } = content;

    if (!userId) {
      console.warn(" [!] UserConsumer: Skipping event due to missing userId");
      return channel.ack(msg);
    }

    if (io) {
      // Emit to the user's specific room
      io.to(`user:${userId}`).emit("buoc_dang_xuat", {
        action,
        deviceId,
        revokedDeviceIds,
      });
      console.log(` [v] UserConsumer: Emitted buoc_dang_xuat for ${userId}`);
    }

    channel.ack(msg);
  } catch (err) {
    console.error(
      " [!] UserConsumer: Error processing user.logout message:",
      err.message,
    );
    // DO NOT requeue on JSON parse errors or other persistent errors
    channel.nack(msg, false, false);
  }
};

const initUserConsumer = async (channel, io) => {
  try {
    await channel.assertExchange(EXCHANGE_NAME, "topic", { durable: true });

    // Assert queue for created
    const qCreated = await channel.assertQueue(QUEUE_CREATED, {
      durable: true,
    });
    await channel.bindQueue(qCreated.queue, EXCHANGE_NAME, ROUTING_KEY_CREATED);
    console.log(
      ` [*] UserConsumer: Listening for events on queue: ${qCreated.queue}`,
    );
    channel.consume(qCreated.queue, (msg) => handleUserEvent(channel, msg), {
      noAck: false,
    });

    // Assert queue for updated
    const qUpdated = await channel.assertQueue(QUEUE_UPDATED, {
      durable: true,
    });
    await channel.bindQueue(qUpdated.queue, EXCHANGE_NAME, ROUTING_KEY_UPDATED);
    console.log(
      ` [*] UserConsumer: Listening for events on queue: ${qUpdated.queue}`,
    );
    channel.consume(
      qUpdated.queue,
      (msg) => handleUserUpdated(channel, msg, io),
      { noAck: false },
    );

    const qLogout = await channel.assertQueue(QUEUE_LOGOUT, {
      durable: true,
    });
    await channel.bindQueue(qLogout.queue, EXCHANGE_NAME, ROUTING_KEY_LOGOUT);
    console.log(
      ` [*] UserConsumer: Listening for events on queue: ${qLogout.queue}`,
    );
    channel.consume(
      qLogout.queue,
      (msg) => handleUserLogout(channel, msg, io),
      { noAck: false },
    );
  } catch (error) {
    console.error(" [!] UserConsumer: Failed to initialize:", error.message);
    throw error;
  }
};

module.exports = { initUserConsumer };
