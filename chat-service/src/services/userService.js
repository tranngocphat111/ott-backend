const User = require("../models/User");
const UserCacheService = require("./userCacheService");

exports.syncUser = async ({ user_id, name }) => {
  const user = await User.findOneAndUpdate(
    { user_id: user_id },
    {
      user_id,
      name,
      is_online: true,
      last_active_at: new Date(),
    },
    { new: true, upsert: true },
  );

  await UserCacheService.setCachedUser(user_id, user);
  return user;
};

exports.getUser = async (user_id) => {
  const cached = await UserCacheService.getCachedUser(user_id);
  if (cached) {
    return cached;
  }

  const user = await User.findOne({ user_id: user_id });
  if (user) {
    await UserCacheService.setCachedUser(user_id, user);
  }

  return user;
};

exports.updateUserStatus = async (user_id, isOnline) => {
  const updatedUser = await User.findOneAndUpdate(
    { user_id: user_id },
    {
      is_online: isOnline,
      last_active_at: new Date(),
    },
    { new: true },
  );

  if (updatedUser) {
    await UserCacheService.setCachedUser(user_id, updatedUser);
  } else {
    await UserCacheService.clearCachedUser(user_id);
  }

  return updatedUser;
};

exports.getAllUsers = async () => {
  return await User.find();
};
