const mongoose = require("mongoose");

const UserSchema = new mongoose.Schema(
  {
    user_id: {
      type: String,
      required: true,
      unique: true,
    },

    name: {
      type: String,
      required: true,
    },


    avatar: {
      type: String,
      default: "",
    },
    phone: {
      type: String,
      default: "",
    },
    email: {
      type: String,
      default: "",
    },

    is_online: {
      type: Boolean,
      required: true,
    },

    last_active_at: {
      type: Date,
      required: true,
    },
  },
  {
    timestamps: true,
  }
);

UserSchema.index({ phone: 1 });
UserSchema.index({ is_online: 1 });
UserSchema.index({ last_active_at: -1 });

module.exports = mongoose.model("User", UserSchema);
