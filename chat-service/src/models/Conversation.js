const mongoose = require("mongoose");

const ConversationSchema = new mongoose.Schema(
  {
    type: {
      type: String,
      enum: ["private", "group"],
      default: "private",
    },

    name: { type: String, default: "" },
    avatar: { type: String, default: "" },

    created_by: {
      type: String,
      ref: "User",  
      required: true,
    },

    member_count: {
      type: Number,
      default: 2,
    },

    last_message: {
      msg_id: { type: String, ref: "Message" },
      sender_id: { type: String, ref: "User" },
      sender_name: { type: String, default: "" },
      content: { type: String },
      type: {
        type: String,
        enum: [
          "text",
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
          "system_friend_request",
        ],
        default: "text",
      },
      createdAt: { type: Date },
    },

    is_deleted: {
      type: Boolean,
      default: false,
    },

    background: { type: String, default: "" },

    is_self_conversation: {
      type: Boolean,
      default: false,
    },

    self_owner_id: {
      type: String,
      default: null,
    },
    
    status: {
      type: String,
      default: "active",
    },

    is_dissolved: {
      type: Boolean,
      default: false,
    },

    // Invite link support
    invite_token: {
      type: String,
      default: null,
      index: { sparse: true },
    },
    
    blocked_user_ids: {
      type: [String],
      default: [],
    },
  },
  {
    timestamps: true,
  },
);

ConversationSchema.index({ type: 1 });
ConversationSchema.index({ is_deleted: 1 });
ConversationSchema.index({ updatedAt: -1 });
ConversationSchema.index(
  { is_self_conversation: 1, self_owner_id: 1 },
  {
    unique: true,
    partialFilterExpression: { is_self_conversation: true },
  },
);

module.exports = mongoose.model("Conversation", ConversationSchema);
