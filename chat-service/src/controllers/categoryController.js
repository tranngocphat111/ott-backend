const CategoryService = require("../services/categoryService");

// Get user categories
exports.getUserCategories = async (req, res) => {
  try {
    const { userId } = req.params;

    let categories = await CategoryService.getUserCategories(userId);

    // Initialize default categories if user has none
    if (categories.length === 0) {
      categories = await CategoryService.initializeDefaultCategories(userId);
    }

    res.status(200).json(categories);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Create category
exports.createCategory = async (req, res) => {
  try {
    const { userId, name, color, order } = req.body;

    const category = await CategoryService.createCategory({
      userId,
      name,
      color,
      order,
    });

    res.status(201).json(category);
  } catch (error) {
    if (error.code === 11000) {
      res.status(400).json({ error: "Category name already exists" });
    } else {
      res.status(500).json({ error: error.message });
    }
  }
};

// Update category
exports.updateCategory = async (req, res) => {
  try {
    const { categoryId } = req.params;
    const { userId, name, color, order } = req.body;

    const category = await CategoryService.updateCategory(
      categoryId,
      userId,
      { name, color, order }
    );

    if (!category) {
      return res.status(404).json({ error: "Category not found" });
    }

    res.status(200).json(category);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

// Delete category
exports.deleteCategory = async (req, res) => {
  try {
    const { categoryId } = req.params;
    const { userId } = req.body;

    const category = await CategoryService.deleteCategory(categoryId, userId);

    if (!category) {
      return res.status(404).json({ error: "Category not found" });
    }

    res.status(200).json({ message: "Category deleted successfully" });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
