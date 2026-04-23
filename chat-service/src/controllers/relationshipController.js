const relationshipService = require("../services/relationshipService");

exports.sendRequest = async (req, res) => {
  try {
    const { requesterId, receiverId } = req.body;
    const result = await relationshipService.sendFriendRequest(requesterId, receiverId);
    const { relationship, conversation, message } = result;

    // Emit socket events
    req.io.to(`user:${receiverId}`).emit("tao_phong_moi", conversation);
    req.io.to(`user:${receiverId}`).emit("tin_nhan", message);
    req.io.to(`user:${requesterId}`).emit("tin_nhan", message);
    req.io.to(`user:${receiverId}`).emit("cap_nhat_quan_he", relationship);
    req.io.to(`user:${requesterId}`).emit("cap_nhat_quan_he", relationship);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.acceptRequest = async (req, res) => {
  try {
    const { relationshipId } = req.params;
    const { relationship, message } = await relationshipService.acceptFriendRequest(relationshipId);

    // Emit socket events to both users
    if (message) {
      req.io.to(`user:${relationship.requester_id}`).emit("tin_nhan", message);
      req.io.to(`user:${relationship.receiver_id}`).emit("tin_nhan", message);
    }
    req.io.to(`user:${relationship.requester_id}`).emit("cap_nhat_quan_he", relationship);
    req.io.to(`user:${relationship.receiver_id}`).emit("cap_nhat_quan_he", relationship);

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
    
    req.io.to(`user:${relationship.requester_id}`).emit("cap_nhat_quan_he", relationship);
    req.io.to(`user:${relationship.receiver_id}`).emit("cap_nhat_quan_he", relationship);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};

exports.cancelRequest = async (req, res) => {
  try {
    const { relationshipId } = req.params;
    const relationship = await relationshipService.cancelFriendRequest(relationshipId);

    req.io.to(`user:${relationship.requester_id}`).emit("cap_nhat_quan_he", relationship);
    req.io.to(`user:${relationship.receiver_id}`).emit("cap_nhat_quan_he", relationship);

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

exports.unfriend = async (req, res) => {
  try {
    const { userId, friendId } = req.body;
    const relationship = await relationshipService.unfriend(userId, friendId);

    req.io.to(`user:${userId}`).emit("cap_nhat_quan_he", relationship);
    req.io.to(`user:${friendId}`).emit("cap_nhat_quan_he", relationship);

    res.status(200).json(relationship);
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
};
