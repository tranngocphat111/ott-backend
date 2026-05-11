// src/services/callStateService.js
const activeCalls = new Map();

const getActiveCall = (conversationId) => {
  return activeCalls.get(conversationId);
};

const getAllActiveCalls = () => {
  return activeCalls;
};

module.exports = {
  activeCalls,
  getActiveCall,
  getAllActiveCalls,
};
