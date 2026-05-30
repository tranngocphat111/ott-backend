const User = require("../models/User");
const UserCacheService = require("./userCacheService");

const DEFAULT_USER_SERVICE_URL = "http://user-service:8082/riff/api";
const ACCOUNT_DISABLED_MESSAGE = "Tài khoản đã bị vô hiệu hóa.";
const ACCOUNT_BLOCKED_MESSAGE = "Tài khoản đang bị khóa.";
const ACCOUNT_DELETED_MESSAGE = "Tài khoản đã bị xóa.";

const normalizeBaseUrl = (value) => String(value || DEFAULT_USER_SERVICE_URL).replace(/\/+$/, "");

const parseDate = (value) => {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const buildAccountError = (code, message) => {
  const error = new Error(message);
  error.code = code;
  error.statusCode = code === "ACCOUNT_DELETED" ? 410 : 403;
  return error;
};

const mapUserResponseToStatus = (user = {}) => ({
  is_active: user.isActive !== false,
  is_blocked: user.isBlocked === true,
  blocked_until: parseDate(user.blockedUntil),
  blocked_reason: user.blockedReason || "",
  deleted_at: parseDate(user.deletedAt),
  status_synced_at: new Date(),
});

const mapStatusEventToUpdate = (event = {}) => {
  const snapshot = event.newStatus || {};
  const update = {
    status_synced_at: new Date(),
  };

  if (snapshot.isActive !== undefined) update.is_active = snapshot.isActive !== false;
  if (snapshot.isBlocked !== undefined) update.is_blocked = snapshot.isBlocked === true;
  if (snapshot.blockedUntil !== undefined) update.blocked_until = parseDate(snapshot.blockedUntil);
  if (snapshot.blockedReason !== undefined) update.blocked_reason = snapshot.blockedReason || "";
  if (snapshot.deletedAt !== undefined) update.deleted_at = parseDate(snapshot.deletedAt);

  const actionType = String(event.actionType || "").toUpperCase();
  if (actionType === "DEACTIVATE" || actionType === "USER_DEACTIVATE") {
    update.is_active = false;
    update.is_blocked = false;
    update.blocked_until = null;
    update.blocked_reason = "";
  } else if (actionType === "RESTORE" || actionType === "USER_RESTORE") {
    update.is_active = true;
    update.deleted_at = null;
  } else if (actionType === "BLOCK" || actionType === "USER_BLOCK") {
    update.is_blocked = true;
    update.blocked_until = parseDate(event.effectiveUntil || snapshot.blockedUntil);
    update.blocked_reason = event.reason || snapshot.blockedReason || "";
  } else if (actionType === "UNBLOCK" || actionType === "USER_UNBLOCK") {
    update.is_blocked = false;
    update.blocked_until = null;
    update.blocked_reason = "";
  }

  return update;
};

const assertActiveStatus = (status = {}) => {
  if (status.deleted_at) {
    throw buildAccountError("ACCOUNT_DELETED", ACCOUNT_DELETED_MESSAGE);
  }

  if (status.is_active === false) {
    throw buildAccountError("ACCOUNT_DISABLED", ACCOUNT_DISABLED_MESSAGE);
  }

  if (status.is_blocked === true) {
    const blockedUntil = parseDate(status.blocked_until);
    if (!blockedUntil || blockedUntil.getTime() > Date.now()) {
      throw buildAccountError("ACCOUNT_BLOCKED", ACCOUNT_BLOCKED_MESSAGE);
    }
  }
};

const getRemoteUserStatus = async (userId) => {
  const internalKey = process.env.INTERNAL_API_KEY;
  if (!internalKey) return null;

  const url = `${normalizeBaseUrl(process.env.USER_SERVICE_URL)}/internal/users/${encodeURIComponent(userId)}`;
  const controller = new AbortController();
  const timeout = setTimeout(
    () => controller.abort(),
    Number(process.env.USER_STATUS_LOOKUP_TIMEOUT_MS || 2500),
  );

  try {
    const response = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        "X-Internal-Key": internalKey,
      },
      signal: controller.signal,
    });

    if (!response.ok) {
      console.warn(`[account-status] user-service returned ${response.status} for ${userId}`);
      return null;
    }

    const body = await response.json();
    return mapUserResponseToStatus(body?.result || {});
  } catch (error) {
    console.warn(`[account-status] failed to load ${userId} from user-service:`, error.message);
    return null;
  } finally {
    clearTimeout(timeout);
  }
};

const getLocalUserStatus = async (userId) => {
  const user = await User.findOne({ user_id: userId })
    .select("is_active is_blocked blocked_until blocked_reason deleted_at")
    .lean();

  if (!user) return null;

  return {
    is_active: user.is_active !== false,
    is_blocked: user.is_blocked === true,
    blocked_until: user.blocked_until || null,
    blocked_reason: user.blocked_reason || "",
    deleted_at: user.deleted_at || null,
  };
};

exports.assertUserCanSendMessage = async (userId) => {
  if (!userId) {
    throw buildAccountError("ACCOUNT_REQUIRED", "Thiếu thông tin người gửi.");
  }

  const remoteStatus = await getRemoteUserStatus(userId);
  if (remoteStatus) {
    await User.findOneAndUpdate(
      { user_id: userId },
      { $set: remoteStatus },
      { new: true },
    );
    await UserCacheService.clearCachedUser(userId);
    assertActiveStatus(remoteStatus);
    return;
  }

  const localStatus = await getLocalUserStatus(userId);
  if (localStatus) {
    assertActiveStatus(localStatus);
  }
};

exports.applyUserStatusChangedEvent = async (event = {}) => {
  const userId = event.userId;
  if (!userId) return null;

  const update = mapStatusEventToUpdate(event);
  const updatedUser = await User.findOneAndUpdate(
    { user_id: userId },
    { $set: update },
    { new: true },
  );

  await UserCacheService.clearCachedUser(userId);
  return updatedUser || update;
};

exports.isRestrictedStatusEvent = (event = {}) => {
  const update = mapStatusEventToUpdate(event);
  try {
    assertActiveStatus(update);
    return false;
  } catch (error) {
    return ["ACCOUNT_DISABLED", "ACCOUNT_BLOCKED", "ACCOUNT_DELETED"].includes(error.code);
  }
};

exports.isAccountRestrictionError = (error) =>
  ["ACCOUNT_DISABLED", "ACCOUNT_BLOCKED", "ACCOUNT_DELETED", "ACCOUNT_REQUIRED"].includes(error?.code);
