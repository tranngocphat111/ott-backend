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
        "poll",
        "system_poll",
        "system_transfer_owner",
        "system_role_change",
        "system_friend_request",
      ],
      default: "text",
    },

    content: [{ type: String }],
    size: { type: Number, default: 0 },
    system_meta: {
      type: mongoose.Schema.Types.Mixed,
      default: null,
    },

    reply_to_msg_id: { type: String, default: null },

    // Poll fields
    poll_question: { type: String, default: null },
    poll_multiple_choice: { type: Boolean, default: false },
    poll_locked: { type: Boolean, default: false },
    poll_locked_at: { type: Date, default: null },
    poll_locked_by: { type: String, ref: "User", default: null },
    poll_options: [
      {
        id: { type: String },
        name: { type: String },
        voters: [{ type: String, ref: "User" }],
      },
    ],

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
MessageSchema.index({ content: 1 });

module.exports = mongoose.model("Message", MessageSchema);
