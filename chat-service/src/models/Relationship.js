const mongoose = require("mongoose");

const RelationshipSchema = new mongoose.Schema(
  {
    requester_id: {
      type: String,
      required: true,
    },
    receiver_id: {
      type: String,
      required: true,
    },
    status: {
      type: String,
      enum: ["PENDING", "ACCEPTED", "BLOCKED", "REMOVED"],
      default: "PENDING",
    },
    relationship_id: {
      type: String, // Shared ID with media-service if available
    },
  },
  {
    timestamps: true,
  }
);

// Index for quick lookup of relationship between two users
RelationshipSchema.index({ requester_id: 1, receiver_id: 1 }, { unique: true });
RelationshipSchema.index({ receiver_id: 1, status: 1 });
RelationshipSchema.index({ requester_id: 1, status: 1 });

module.exports = mongoose.model("Relationship", RelationshipSchema);
