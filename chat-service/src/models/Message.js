const mongoose = require("mongoose");
const { generateId } = require("../utils/snowflake");

const MEDIA_MESSAGE_TYPES = new Set(["image", "video", "file", "audio"]);

const isEmptyPlainObject = (value) =>
  value &&
  typeof value === "object" &&
  !Array.isArray(value) &&
  Object.prototype.toString.call(value) === "[object Object]" &&
  Object.keys(value).length === 0;

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

    content: { type: [{ type: String }], default: undefined },
    size: { type: Number },
    system_meta: { type: mongoose.Schema.Types.Mixed },

    reply_to_msg_id: { type: String },

    // Poll fields
    poll_question: { type: String },
    poll_multiple_choice: { type: Boolean },
    poll_locked: { type: Boolean },
    poll_locked_at: { type: Date },
    poll_locked_by: { type: String, ref: "User" },
    poll_options: {
      type: [
        {
          _id: false,
          id: { type: String },
          name: { type: String },
          voters: {
            type: [{ type: String, ref: "User" }],
            default: undefined,
          },
        },
      ],
      default: undefined,
    },

    reactions: {
      type: [
        {
          _id: false,
          user_id: { type: String, ref: "User" },
          type: { type: String },
        },
      ],
      default: undefined,
    },

    is_deleted: { type: Boolean },
    is_revoked: { type: Boolean },
    deleted_for: {
      type: [{ type: String, ref: "User" }],
      default: undefined,
    },

    // Pinned message fields
    is_pinned: { type: Boolean },
    pinned_at: { type: Date },
    pinned_by: { type: String, ref: "User" },
  },
  {
    timestamps: true,
  },
);

MessageSchema.pre("validate", function stripUnusedFields() {
  const messageType = String(this.type || "text");
  const numericSize = Number(this.size);

  if (
    !MEDIA_MESSAGE_TYPES.has(messageType) ||
    !Number.isFinite(numericSize) ||
    numericSize <= 0
  ) {
    this.size = undefined;
  }

  if (!this.reply_to_msg_id) {
    this.reply_to_msg_id = undefined;
  }

  if (this.system_meta == null || isEmptyPlainObject(this.system_meta)) {
    this.system_meta = undefined;
  }

  if (messageType !== "poll") {
    this.poll_question = undefined;
    this.poll_multiple_choice = undefined;
    this.poll_locked = undefined;
    this.poll_locked_at = undefined;
    this.poll_locked_by = undefined;
    this.poll_options = undefined;
  } else {
    if (!this.poll_question) {
      this.poll_question = undefined;
    }

    if (this.poll_multiple_choice !== true) {
      this.poll_multiple_choice = undefined;
    }

    if (this.poll_locked !== true) {
      this.poll_locked = undefined;
      this.poll_locked_at = undefined;
      this.poll_locked_by = undefined;
    }

    if (!Array.isArray(this.poll_options) || this.poll_options.length === 0) {
      this.poll_options = undefined;
    } else {
      this.poll_options.forEach((option) => {
        if (!Array.isArray(option.voters) || option.voters.length === 0) {
          option.voters = undefined;
        }
      });
    }
  }

  if (!Array.isArray(this.reactions) || this.reactions.length === 0) {
    this.reactions = undefined;
  }

  if (!Array.isArray(this.deleted_for) || this.deleted_for.length === 0) {
    this.deleted_for = undefined;
  }

  if (this.is_deleted !== true) {
    this.is_deleted = undefined;
  }

  if (this.is_revoked !== true) {
    this.is_revoked = undefined;
  }

  if (this.is_pinned !== true) {
    this.is_pinned = undefined;
    this.pinned_at = undefined;
    this.pinned_by = undefined;
  }
});

MessageSchema.index({ conversation_id: 1, msg_id: -1 });
MessageSchema.index({ conversation_id: 1, is_pinned: 1 });
MessageSchema.index({ conversation_id: 1, type: 1 });
MessageSchema.index({ content: 1 });

module.exports = mongoose.model("Message", MessageSchema);
