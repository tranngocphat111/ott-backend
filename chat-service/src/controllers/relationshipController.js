const relationshipService = require("../services/relationshipService");
const ConversationService = require("../services/conversationService");

const buildRelationshipUpdatePayload = (relationship, typeOverride = null, actorId = null) => {
  if (!relationship) return null;
  const raw =
    typeof relationship.toObject === "function"
      ? relationship.toObject()
      : relationship;
  const relationshipId = raw._id || raw.id || raw.relationship_id;
  const requesterId = raw.requester_id || raw.requesterId;
  const receiverId = raw.receiver_id || raw.receiverId;
  const status = raw.status ? String(raw.status).toUpperCase() : raw.status;

  return {
    ...raw,
    _id: raw._id,
    id: raw.id || raw._id,
    relationshipId,
    relationship_id: raw.relationship_id,
    requesterId,
    receiverId,
    requester_id: requesterId,
    receiver_id: receiverId,
    status,
    type: typeOverride || status,
    actorId,
    timestamp: new Date().toISOString(),
  };
};

const emitRelationshipUpdate = (io, relationship, typeOverride = null, actorId = null) => {
  const payload = buildRelationshipUpdatePayload(relationship, typeOverride, actorId);
  if (!io || !payload?.requesterId || !payload?.receiverId) return;

  io.to(`user:${payload.requesterId}`).emit("cap_nhat_quan_he", payload);
  io.to(`user:${payload.receiverId}`).emit("cap_nhat_quan_he", payload);
};

exports.sendRequest = async (req, res) => {
  try {
    const { requesterId, receiverId } = req.body;
    const result = await relationshipService.sendFriendRequest(requesterId, receiverId);
    const { relationship, conversation, message } = result;
    const detailedConversation = conversation?._id
      ? await ConversationService.getConversationById(conversation._id)
      : null;
    const conversationPayload = detailedConversation || conversation;

    // Emit socket events for new conversation and system message (not yet moved to MQ)
    if (conversationPayload) {
      req.io.to(`user:${requesterId}`).emit("tao_phong_moi", conversationPayload);
      req.io.to(`user:${receiverId}`).emit("tao_phong_moi", conversationPayload);
    }
    if (message) {
      req.io.to(`user:${receiverId}`).emit("tin_nhan", message);
      req.io.to(`user:${requesterId}`).emit("tin_nhan", message);
    }
    emitRelationshipUpdate(req.io, relationship, "REQUEST_SENT", requesterId);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.acceptRequest = async (req, res) => {
  try {
    const { relationshipId } = req.params;
    const { relationship, message } = await relationshipService.acceptFriendRequest(relationshipId);

    // Emit socket events for system message
    if (message) {
      req.io.to(`user:${relationship.requester_id}`).emit("tin_nhan", message);
      req.io.to(`user:${relationship.receiver_id}`).emit("tin_nhan", message);
    }
    emitRelationshipUpdate(req.io, relationship, "REQUEST_ACCEPTED", relationship.receiver_id);

    res.status(200).json(relationship);
  } catch (error) {
    console.error("Accept Request Error:", error);
    res.status(400).json({ error: error.message });
  }
};

exports.rejectRequest = async (req, res) => {
  try {
    const { relationshipId } = req.params;
    const relationship = await relationshipService.rejectFriendRequest(relationshipId);
    emitRelationshipUpdate(req.io, relationship, "REQUEST_REJECTED", relationship.receiver_id);
    
    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.cancelRequest = async (req, res) => {
  try {
    const { relationshipId } = req.params;
    const relationship = await relationshipService.cancelFriendRequest(relationshipId);
    emitRelationshipUpdate(req.io, relationship, "REQUEST_CANCELLED", relationship.requester_id);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.getStatus = async (req, res) => {
  try {
    const { userId1, userId2 } = req.query;
    const relationship = await relationshipService.getRelationshipBetween(userId1, userId2);
    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.getFriends = async (req, res) => {
  try {
    const { userId } = req.params;
    const friends = await relationshipService.getFriends(userId);
    res.status(200).json(friends);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.getBlockedUsers = async (req, res) => {
  try {
    const { userId } = req.params;
    const blockedUsers = await relationshipService.getBlockedUsers(userId);
    res.status(200).json(blockedUsers);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.unfriend = async (req, res) => {
  try {
    const { userId, friendId } = req.body;
    console.log(`[RelationshipController] Unfriend request from ${userId} for friend ${friendId}`);
    const relationship = await relationshipService.unfriend(userId, friendId);
    emitRelationshipUpdate(req.io, relationship, "UNFRIENDED", userId);

    res.status(200).json(relationship);
  } catch (error) {
    console.error(`[RelationshipController] Unfriend error: ${error.message}`);
    res.status(400).json({ error: error.message });
  }
};

exports.blockUser = async (req, res) => {
  try {
    const { userId, targetId } = req.body;
    const relationship = await relationshipService.blockUser(userId, targetId);
    emitRelationshipUpdate(req.io, relationship, "BLOCKED", userId);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.unblockUser = async (req, res) => {
  try {
    const { userId, targetId } = req.body;
    const relationship = await relationshipService.unblockUser(userId, targetId);
    emitRelationshipUpdate(req.io, relationship, "UNFRIENDED", userId);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};
