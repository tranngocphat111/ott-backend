const mongoose = require("mongoose");

const UserCategorySchema = new mongoose.Schema(
  {
    user_id: {
      type: String,
      required: true,
      ref: "User",
    },

    name: {
      type: String,
      required: true,
    },

    color: {
      type: String,
      required: true,
      default: "#3B82F6",
    },

    order: {
      type: Number,
      default: 0,
    },

    is_default: {
      type: Boolean,
      default: false,
    },
  },
  {
    timestamps: true,
  }
);

UserCategorySchema.index({ user_id: 1, order: 1 });
UserCategorySchema.index({ user_id: 1, name: 1 }, { unique: true });

module.exports = mongoose.model("UserCategory", UserCategorySchema);
