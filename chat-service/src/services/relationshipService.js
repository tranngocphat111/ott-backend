const Relationship = require("../models/Relationship");
const { publishRelationshipEvent } = require("../events/relationshipEvents");
const { publishNotification } = require("../events/notificationEvents");
const mongoose = require("mongoose");

const getUserDisplayName = async (userId) => {
  const User = require("../models/User");
  const user = await User.findOne({ user_id: userId }).select("name").lean();
  return String(user?.name || "").trim() || "Người dùng";
};

exports.getRelationshipBetween = async (userId1, userId2) => {
  return await Relationship.findOne({
    $or: [
      { requester_id: userId1, receiver_id: userId2 },
      { requester_id: userId2, receiver_id: userId1 },
    ],
  });
};

const findRelationshipByAnyId = async (relationshipId) => {
  const normalizedId = String(relationshipId || "").trim();
  if (!normalizedId) return null;

  if (mongoose.Types.ObjectId.isValid(normalizedId)) {
    const relationship = await Relationship.findById(normalizedId);
    if (relationship) return relationship;
  }

  return Relationship.findOne({ relationship_id: normalizedId });
};

const ensureFriendRequestSentMessage = async (
  relationship,
  requesterId,
  receiverId,
  { reuseExisting = false, requesterName: requesterNameOverride } = {},
) => {
  const ConversationService = require("./conversationService");
  const MessageService = require("./messageService");
  const Message = require("../models/Message");

  const conversation = await ConversationService.findOrCreatePrivateConversation(
    requesterId,
    receiverId,
  );
  const relationshipId = relationship._id.toString();
  const requesterName =
    String(requesterNameOverride || "").trim() ||
    await getUserDisplayName(requesterId);
  const messageContent = `${requesterName} đã gửi lời mời kết bạn`;

  let message = null;
  if (reuseExisting) {
    message = await Message.findOne({
      conversation_id: conversation._id,
      type: "system_friend_request",
      "system_meta.action": "friend_request_sent",
      "system_meta.relationship_id": relationshipId,
    }).lean();

    if (message) {
      const currentContent = Array.isArray(message.content)
        ? String(message.content[0] || "")
        : String(message.content || "");
      const nextSystemMeta = {
        ...(message.system_meta || {}),
        requester_name: requesterName,
      };
      const shouldUpdateMessage =
        currentContent !== messageContent ||
        String(message.system_meta?.requester_name || "") !== requesterName;

      if (shouldUpdateMessage) {
        const updatedMessage = await Message.findByIdAndUpdate(
          message._id,
          {
            content: [messageContent],
            system_meta: nextSystemMeta,
          },
          { new: true },
        ).lean();
        if (updatedMessage) {
          message = updatedMessage;
        }
      }

      message = {
        ...message,
        sender_name: requesterName,
      };
    }
  }

  if (!message) {
    message = await MessageService.sendMessage({
      conversationId: conversation._id,
      senderId: requesterId,
      content: messageContent,
      type: "system_friend_request",
      systemMeta: {
        action: "friend_request_sent",
        relationship_id: relationshipId,
        requester_id: requesterId,
        receiver_id: receiverId,
        requester_name: requesterName,
      },
    });
  }

  return { conversation, message };
};

exports.sendFriendRequest = async (requesterId, receiverId) => {
  if (requesterId === receiverId) {
    throw new Error("Không thể tự kết bạn với chính mình.");
  }

  let relationship = await exports.getRelationshipBetween(requesterId, receiverId);

  if (relationship) {
    if (relationship.status === "ACCEPTED") {
      throw new Error("Hai người đã là bạn bè.");
    }
    if (relationship.status === "PENDING") {
      if (String(relationship.requester_id) === String(requesterId)) {
        const { conversation, message } = await ensureFriendRequestSentMessage(
          relationship,
          requesterId,
          receiverId,
          { reuseExisting: true },
        );
        return { relationship, conversation, message };
      }
      throw new Error("Người này đã gửi lời mời kết bạn cho bạn.");
    }
    if (relationship.status === "BLOCKED") {
      if (relationship.requester_id === requesterId) {
        throw new Error("Bạn đang chặn người này. Hãy bỏ chặn trước khi kết bạn.");
      } else {
        throw new Error("Bạn đã bị người này chặn.");
      }
    }
    // If it was REMOVED, we update it
    relationship.requester_id = requesterId;
    relationship.receiver_id = receiverId;
    relationship.status = "PENDING";
  } else {
    relationship = new Relationship({
      requester_id: requesterId,
      receiver_id: receiverId,
      status: "PENDING",
    });
  }

  await relationship.save();
  const requesterName = await getUserDisplayName(requesterId);
  try {
    await publishRelationshipEvent("REQUEST_SENT", relationship);
    await publishNotification({
      recipientId: receiverId,
      senderId: requesterId,
      type: "FRIEND_REQUEST",
      content: `${requesterName} đã gửi cho bạn lời mời kết bạn`,
      referenceId: relationship._id.toString()
    });
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish REQUEST_SENT event: ${err.message}`);
  }

  const { conversation, message } = await ensureFriendRequestSentMessage(
    relationship,
    requesterId,
    receiverId,
    { requesterName },
  );

  return { relationship, conversation, message };
};

exports.acceptFriendRequest = async (relationshipId) => {
  const relationship = await findRelationshipByAnyId(relationshipId);
  if (!relationship) throw new Error("Không tìm thấy quan hệ.");

  relationship.status = "ACCEPTED";
  await relationship.save();

  try {
    await publishRelationshipEvent("REQUEST_ACCEPTED", relationship);
    await publishNotification({
      recipientId: relationship.requester_id,
      senderId: relationship.receiver_id,
      type: "FRIEND_ACCEPTED",
      content: "Đã chấp nhận lời mời kết bạn của bạn",
      referenceId: relationship._id.toString()
    });
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish REQUEST_ACCEPTED event: ${err.message}`);
  }

  // --- NEW: Send system message ---
  const ConversationService = require("./conversationService");
  const MessageService = require("./messageService");
  
  console.log("Creating/finding conversation for:", relationship.requester_id, relationship.receiver_id);
  const conversation = await ConversationService.findOrCreatePrivateConversation(relationship.requester_id, relationship.receiver_id);
  
  console.log("Sending system message for acceptance...");
  const message = await MessageService.sendMessage({
    conversationId: conversation._id,
    senderId: relationship.receiver_id, // Receiver accepts, so they are the "sender" of the event
    content: "Hai bạn đã trở thành bạn bè. Hãy bắt đầu trò chuyện!",
    type: "system_add", // Or a new type if preferred
    systemMeta: {
      action: "friend_request_accepted",
      relationship_id: relationship._id.toString(),
      requester_id: relationship.requester_id,
      receiver_id: relationship.receiver_id,
    },
  });

  return { relationship, message };
};

exports.updateRelationshipFromEvent = async (payload) => {
  const { requesterId, receiverId, status, relationshipId, source } = payload;
  if (!requesterId || !receiverId || !status) return null;

  const existing = await exports.getRelationshipBetween(requesterId, receiverId);
  if (!existing && status === "REMOVED") {
    return null;
  }

  const update = {
    requester_id: requesterId,
    receiver_id: receiverId,
    status: status,
  };

  if (relationshipId && source !== "chat-service") {
    update.relationship_id = relationshipId;
  }

  const relationship = await Relationship.findOneAndUpdate(
    {
      $or: [
        { requester_id: requesterId, receiver_id: receiverId },
        { requester_id: receiverId, receiver_id: requesterId },
      ],
    },
    update,
    { upsert: true, new: true }
  );

  return relationship;
};

exports.rejectFriendRequest = async (relationshipId) => {
  const relationship = await findRelationshipByAnyId(relationshipId);
  if (!relationship) throw new Error("Không tìm thấy quan hệ.");

  relationship.status = "REMOVED";
  await relationship.save();

  try {
    await publishRelationshipEvent("REQUEST_REJECTED", relationship);
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish REQUEST_REJECTED event: ${err.message}`);
  }
  return relationship;
};

exports.cancelFriendRequest = async (relationshipId) => {
  const relationship = await findRelationshipByAnyId(relationshipId);
  if (!relationship) throw new Error("Không tìm thấy quan hệ.");

  relationship.status = "REMOVED";
  await relationship.save();

  try {
    await publishRelationshipEvent("REQUEST_CANCELLED", relationship);
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish REQUEST_CANCELLED event: ${err.message}`);
  }
  return relationship;
};

exports.getFriends = async (userId) => {
  const relationships = await Relationship.find({
    $or: [
      { requester_id: userId, status: "ACCEPTED" },
      { receiver_id: userId, status: "ACCEPTED" },
    ],
  }).lean();

  const friendIds = relationships.map(rel => 
    rel.requester_id === userId ? rel.receiver_id : rel.requester_id
  );

  const User = require("../models/User");
  return await User.find({ user_id: { $in: friendIds } }).lean();
};

exports.unfriend = async (userId, friendId) => {
  console.log(`[RelationshipService] Unfriending userId: ${userId}, friendId: ${friendId}`);
  const relationship = await exports.getRelationshipBetween(userId, friendId);
  if (!relationship) {
    console.error(`[RelationshipService] Relationship not found between ${userId} and ${friendId}`);
    throw new Error("Không tìm thấy mối quan hệ bạn bè.");
  }
  
  console.log(`[RelationshipService] Found relationship with status: ${relationship.status}`);
  if (relationship.status !== "ACCEPTED") {
    throw new Error("Hai người hiện không là bạn bè.");
  }

  relationship.status = "REMOVED";
  await relationship.save();
  console.log("[RelationshipService] DB updated to REMOVED");

  try {
    await publishRelationshipEvent("UNFRIENDED", relationship);
    console.log("[RelationshipService] Event published successfully");
  } catch (err) {
    console.error("[RelationshipService] Failed to publish event, but DB was updated:", err.message);
    // We don't rethrow here to allow the API to return success since the DB was updated
  }
  
  return relationship;
};

exports.blockUser = async (blockerId, blockedId) => {
  if (blockerId === blockedId) {
    throw new Error("Không thể tự chặn chính mình.");
  }

  let relationship = await exports.getRelationshipBetween(blockerId, blockedId);

  if (relationship) {
    relationship.requester_id = blockerId;
    relationship.receiver_id = blockedId;
    relationship.status = "BLOCKED";
  } else {
    relationship = new Relationship({
      requester_id: blockerId,
      receiver_id: blockedId,
      status: "BLOCKED",
    });
  }

  await relationship.save();
  
  try {
    await publishRelationshipEvent("USER_BLOCKED", relationship);
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish USER_BLOCKED event: ${err.message}`);
  }

  return relationship;
};

exports.unblockUser = async (blockerId, blockedId) => {
  const relationship = await Relationship.findOne({
    requester_id: blockerId,
    receiver_id: blockedId,
    status: "BLOCKED",
  });

  if (!relationship) {
    throw new Error("Không tìm thấy yêu cầu chặn từ bạn đối với người này.");
  }

  relationship.status = "REMOVED";
  await relationship.save();

  try {
    await publishRelationshipEvent("USER_UNBLOCKED", relationship);
  } catch (err) {
    console.error(`[RelationshipService] Failed to publish USER_UNBLOCKED event: ${err.message}`);
  }

  return relationship;
};

exports.checkBlockStatus = async (userId1, userId2) => {
  const relationship = await exports.getRelationshipBetween(userId1, userId2);
  if (!relationship || relationship.status !== "BLOCKED") {
    return { isBlocked: false, blockerId: null };
  }

  return {
    isBlocked: true,
    blockerId: relationship.requester_id,
  };
};
