const User = require("../models/User");
const UserCacheService = require("./userCacheService");
const mongoose = require("mongoose");

const extractAvatarPath = (avatarUrl) => {
  if (!avatarUrl) return "";
  const str = String(avatarUrl).trim();
  if (str.startsWith("http")) return str;
  if (str.startsWith("/")) return str;
  
  try {

    return url.pathname;
  } catch (e) {
    return str;
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

exports.updateUserInfo = async (userData) => {
  const { userId, fullName, avatarUrl, coverUrl, bio, email, phone } = userData;

  const updatePayload = {};
  if (fullName !== undefined) updatePayload.name = fullName;
  if (avatarUrl !== undefined) updatePayload.avatar = extractAvatarPath(avatarUrl);
  if (coverUrl !== undefined) updatePayload.cover_url = extractAvatarPath(coverUrl);
  if (bio !== undefined) updatePayload.bio = bio;
  if (email !== undefined) updatePayload.email = email;
  if (phone !== undefined) updatePayload.phone = phone;

  const updatedUser = await User.findOneAndUpdate(
    { user_id: userId },
    updatePayload,
    { new: true }
  );

  if (updatedUser) {
    await UserCacheService.setCachedUser(userId, updatedUser);
  }

  return updatedUser;
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

exports.updateUser = async (userData) => {
  const { userId, avatar, displayName } = userData;
  const updateData = {};
  if (avatar !== undefined) updateData.avatar = extractAvatarPath(avatar);
  if (displayName !== undefined) updateData.name = displayName;
  
  const updatedUser = await User.findOneAndUpdate(
    { user_id: userId },
    { $set: updateData },
    { new: true }
  );

  if (updatedUser) {
    await UserCacheService.setCachedUser(userId, updatedUser);
  }
  return updatedUser;
};

exports.getUser = async (user_id) => {
  const cached = await UserCacheService.getCachedUser(user_id);
  if (cached) {
    return cached;
  }

  let user = await User.findOne({ user_id: user_id });
  if (!user && mongoose.Types.ObjectId.isValid(String(user_id || ""))) {
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
  // Normalize phone formats for searching locally (handle 0 vs 84)
  const variants = [phone];
  if (phone.startsWith('0')) {
    variants.push('84' + phone.substring(1));
  } else if (phone.startsWith('84')) {
    variants.push('0' + phone.substring(2));
  }

  return await User.findOne({
    phone: { $in: variants }
  });
};
