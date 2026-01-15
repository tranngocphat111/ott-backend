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
