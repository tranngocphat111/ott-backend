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
      content: { type: String },
      type: {
        type: String,
        enum: ["text", "image", "video", "file"],
        default: "text",
      },
      createdAt: { type: Date },
    },

    is_deleted: {
      type: Boolean,
      default: false,
    },

    background: { type: String, default: "" },
  },
  {
    timestamps: true,
  }
);

ConversationSchema.index({ type: 1 });
ConversationSchema.index({ is_deleted: 1 });
ConversationSchema.index({ updatedAt: -1 });

module.exports = mongoose.model("Conversation", ConversationSchema);
