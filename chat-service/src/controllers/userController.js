const UserService = require("../services/userService");

exports.syncUser = async (req, res) => {
  try {
    const { user_id, name } = req.body;
    const user = await UserService.syncUser({ user_id, name });
    res.status(200).json(user);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

exports.getAllUsers = async (req, res) => {
  try {
    const users = await UserService.getAllUsers();
    res.status(200).json(users);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};