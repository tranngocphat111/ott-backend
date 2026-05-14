const UserCategory = require("../models/UserCategory");

// Get all categories for a user
exports.getUserCategories = async (userId) => {
  return await UserCategory.find({ user_id: userId }).sort({ order: 1 });
};

// Create new category
exports.createCategory = async ({ userId, name, color, order }) => {
  const newCategory = new UserCategory({
    user_id: userId,
    name,
    color: color || "#3B82F6",
    order: order || 0,
  });

  return await newCategory.save();
};

// Update category
exports.updateCategory = async (categoryId, userId, updateData) => {
  return await UserCategory.findOneAndUpdate(
    { _id: categoryId, user_id: userId },
    updateData,
    { new: true }
  );
};

// Delete category
exports.deleteCategory = async (categoryId, userId) => {
  return await UserCategory.findOneAndDelete({
    _id: categoryId,
    user_id: userId,
  });
};

