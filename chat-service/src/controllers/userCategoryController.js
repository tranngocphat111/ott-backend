const UserCategory = require("../models/UserCategory");

// Get user categories
exports.getUserCategories = async (req, res) => {
  try {
    const { userId } = req.params;
    const categories = await UserCategory.find({ user_id: userId }).sort({ order: 1, createdAt: 1 });
    res.status(200).json(categories);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Create new category
exports.createCategory = async (req, res) => {
  try {
    const { userId, name, color, icon, order } = req.body;
    const newCategory = new UserCategory({
      user_id: userId,
      name,
      color,
      icon: icon || "",
      order: order || 0,
    });
    const category = await newCategory.save();
    res.status(201).json(category);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Update category
exports.updateCategory = async (req, res) => {
  try {
    const { categoryId } = req.params;
    const { name, color, icon, order } = req.body;
    const category = await UserCategory.findByIdAndUpdate(
      categoryId,
      { name, color, icon, order },
      { new: true }
    );
    res.status(200).json(category);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Delete category
exports.deleteCategory = async (req, res) => {
  try {
    const { categoryId } = req.params;
    await UserCategory.findByIdAndDelete(categoryId);
    res.status(200).json({ message: "Category deleted successfully" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Create default categories for new user
exports.createDefaultCategories = async (req, res) => {
  try {
    const { userId } = req.body;
    const defaultCategories = [
      { name: "Khách hàng", color: "#EF4444", order: 1 },
      { name: "Gia đình", color: "#10B981", order: 2 },
      { name: "Công việc", color: "#F97316", order: 3 },
      { name: "Bạn bè", color: "#8B5CF6", order: 4 },
      { name: "Trả lời sau", color: "#EAB308", order: 5 },
    ];

    const categories = defaultCategories.map((cat) => ({
      user_id: userId,
      name: cat.name,
      color: cat.color,
      order: cat.order,
      is_default: true,
    }));

    const result = await UserCategory.insertMany(categories);
    res.status(201).json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
