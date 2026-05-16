const { AccessToken } = require("livekit-server-sdk");

/**
 * Generate a token for a user to join a LiveKit room.
 * @param {string} roomName - The name of the room (e.g., conversationId).
 * @param {string} participantName - The user's name or ID.
 * @param {{ name?: string, metadata?: string }} participantInfo - Display metadata for other participants.
 * @returns {string} The generated token.
 */
exports.generateToken = async (roomName, participantName, participantInfo = {}) => {
  const apiKey = process.env.LIVEKIT_API_KEY?.trim();
  const apiSecret = process.env.LIVEKIT_API_SECRET?.trim();

  if (!apiKey || !apiSecret) {
    console.error("ERROR: Missing LiveKit API Key or Secret in process.env");
    return null;
  }

  // Log để kiểm tra (chỉ hiện 4 ký tự đầu để bảo mật)
  console.log(`[LiveKit] Generating token for Room: ${roomName}, Participant: ${participantName}`);
  console.log(`[LiveKit] Using Key: ${apiKey.substring(0, 4)}..., Secret: ${apiSecret.substring(0, 4)}...`);

  const at = new AccessToken(apiKey, apiSecret, {
    identity: String(participantName),
    name: String(participantInfo.name || "").trim() || undefined,
    metadata: String(participantInfo.metadata || "").trim() || undefined,
  });

  at.addGrant({
    roomJoin: true,
    room: String(roomName),
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });

  return await at.toJwt();
};
