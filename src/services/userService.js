const User = require("../models/User");

exports.syncUser = async ({ user_id, name }) => {
  return await User.findOneAndUpdate(
    { user_id: user_id },
    {
      user_id,
      name,
      is_online: true,
      last_active_at: new Date(),
    },
    { new: true, upsert: true }
  );
};

exports.getUser = async (user_id) => {
  return await User.findOne({ user_id: user_id });
};

exports.updateUserStatus = async (user_id, isOnline) => {
  return await User.findOneAndUpdate(
    { user_id: user_id },
    {
      is_online: isOnline,
      last_active_at: new Date(),
    },
    { new: true }
  );
};

exports.getAllUsers = async () => {
  return await User.find().sort({ last_active_at: -1 });
};
