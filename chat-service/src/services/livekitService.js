const { AccessToken } = require("livekit-server-sdk");
const dotenv = require("dotenv");

const path = require("path");
dotenv.config({ path: path.resolve(__dirname, "../../../.env") });

const LIVEKIT_API_KEY = process.env.LIVEKIT_API_KEY || "devkey";
const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET || "secret";

/**
 * Generate a token for a user to join a LiveKit room.
 * @param {string} roomName - The name of the room (e.g., conversationId).
 * @param {string} participantName - The user's name or ID.
 * @returns {string} The generated token.
 */
exports.generateToken = (roomName, participantName) => {
  const at = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET, {
    identity: participantName,
  });

  at.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true,
  });

  return at.toJwt();
};
