const mongoose = require("mongoose");
const { generateId } = require("../utils/snowflake");

const MessageSchema = new mongoose.Schema(
  {
    msg_id: {
      type: String,
      default: generateId,
      unique: true,
    },

    conversation_id: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Conversation",
      required: true,
    },

    sender_id: {
      type: String,
      ref: "User",
      required: true,
    },

    type: {
      type: String,
      enum: ["text", "image", "video", "file"],
      default: "text",
    },

    content: [{ type: String }],
    size: { type: Number, default: 0 },

    reply_to_msg_id: { type: String, default: null },

    reactions: [
      {
        user_id: { type: String, ref: "User" },
        type: { type: String },
      },
    ],

    is_deleted: { type: Boolean, default: false },
    is_revoked: { type: Boolean, default: false },
  },
  {
    timestamps: true,
  }
);

MessageSchema.index({ conversation_id: 1, msg_id: -1 });

module.exports = mongoose.model("Message", MessageSchema);
