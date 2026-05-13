const mongoose = require("mongoose");

const ParticipantSchema = new mongoose.Schema(
  {
    user_id: {
      type: String,
      required: true,
      ref: "User",
    },

    conversation_id: {
      type: mongoose.Schema.Types.ObjectId,
      required: true,
      ref: "Conversation",
    },

    last_delivered_message_id: {
      type: String,
      required: true,
      default: "0",
    },

    last_delivered_at: {
      type: Date,
      default: null,
    },

    last_read_message_id: {
      type: String,
      required: true,
      default: "0",
    },

    last_read_at: {
      type: Date,
      required: true,
      default: Date.now,
    },

    deleted_msg_id: {
      type: String,
      default: "0",
    },

    settings: {
      category_id: {
        type: mongoose.Schema.Types.ObjectId,
        ref: "UserCategory",
        default: null,
      },
      is_pinned: {
        type: Boolean,
        default: false,
      },
      pinned_at: {
        type: Date,
      },
      notification_status: {
        type: String,
        enum: ["on", "mute", "off"],
        default: "on",
      },
      mute_until: {
        type: Date,
        default: null,
      },
      removed_from_group_at: {
        type: Date,
        default: null,
      },
      removed_by: {
        type: String,
        default: null,
      },
      group_dissolved_at: {
        type: Date,
        default: null,
      },
      group_dissolved_by: {
        type: String,
        default: null,
      },
    },

    nickname: { type: String },

    joined_at: {
      type: Date,
      required: true,
      default: Date.now,
    },

    added_by: { type: String },

    roles: {
      type: String,
      enum: ["admin", "user"],
      default: "user",
    },
    
    status: {
      type: String,
      enum: ["invited", "joined"],
      default: "joined",
    },
  },
  {
    timestamps: true,
  },
);

ParticipantSchema.index({ conversation_id: 1, user_id: 1 }, { unique: true });
ParticipantSchema.index({ conversation_id: 1 });
ParticipantSchema.index({ user_id: 1, updatedAt: -1 });

module.exports = mongoose.model("Participant", ParticipantSchema);
