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
      enum: [
        "text",
        "link",
        "image",
        "video",
        "file",
        "audio",
        "system_add",
        "system_block",
        "system_leave",
        "system_pin",
        "system_unpin",
        "call_start",
        "call_join",
        "call_end",
        "call_missed",
        "call_cancel",
        "call_no_answer",
      ],
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
    deleted_for: [{ type: String, ref: "User" }],

    // Pinned message fields
    is_pinned: { type: Boolean, default: false },
    pinned_at: { type: Date, default: null },
    pinned_by: { type: String, ref: "User", default: null },
  },
  {
    timestamps: true,
  },
);

MessageSchema.index({ conversation_id: 1, msg_id: -1 });
MessageSchema.index({ conversation_id: 1, is_pinned: 1 });
MessageSchema.index({ conversation_id: 1, type: 1 });

module.exports = mongoose.model("Message", MessageSchema);
