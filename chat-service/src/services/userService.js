const User = require("../models/User");
const UserCacheService = require("./userCacheService");

const extractAvatarPath = (avatarUrl) => {
  if (!avatarUrl) return "";
  try {
    // If it's already a path, return it
    if (avatarUrl.startsWith("/")) return avatarUrl;
    
    const url = new URL(avatarUrl);
    // Extract path from S3 or other full URLs
    // e.g., https://bucket.s3.region.amazonaws.com/avatar/abc.png -> /avatar/abc.png
    return url.pathname;
  } catch (e) {
    return avatarUrl;
  }
};

exports.createUser = async (userData) => {
  const { userId, username, avatar, phone, email } = userData;

  const existingUser = await User.findOne({ user_id: userId });
  if (existingUser) {
    return existingUser;
  }

  const newUser = new User({
    user_id: userId,
    name: username || "Người dùng",
    avatar: extractAvatarPath(avatar),
    phone: phone || "",
    email: email || "",
    is_online: false,
    last_active_at: new Date(),
  });

  await newUser.save();
  await UserCacheService.setCachedUser(userId, newUser);
  return newUser;
};

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

  let user = await User.findOne({ user_id: user_id });
  if (!user) {
    user = await User.findById(user_id);
  }
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

exports.getUserByPhone = async (phone) => {
  return await User.findOne({ phone: phone });
};
