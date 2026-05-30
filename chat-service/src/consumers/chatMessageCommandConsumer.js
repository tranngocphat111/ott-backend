const MessageService = require("../services/messageService");
const ParticipantService = require("../services/participantService");
const {
  QUEUES,
  assertTopology,
} = require("../events/chatCommandEvents");
const { publishMessageCreated } = require("../events/chatEvents");

const parsePositiveInt = (value, fallback) => {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
};
const queuedAccountStatusCheckEnabled =
  String(
    process.env.CHAT_QUEUED_SEND_ACCOUNT_STATUS_CHECK_ENABLED || "false",
  ).toLowerCase() === "true";

const safeParse = (buffer) => {
  try {
    return JSON.parse(buffer.toString());
  } catch {
    return null;
  }
};

const emitMessageToParticipants = async (io, conversationId, message) => {
  if (!io || !conversationId || !message) return;

  const participants = await ParticipantService.getJoinedParticipants(
    conversationId,
  );

  participants.forEach((participant) => {
    io.to(`user:${participant.user_id}`).emit("tin_nhan", message);
  });
};

const publishCreatedOrFallback = async (io, payload, message) => {
  try {
    await publishMessageCreated({
      ...payload,
      message,
    });
  } catch (error) {
    console.warn(
      "[chat-command] publish message.created failed; emitting socket fallback:",
      error?.message || error,
    );
    await emitMessageToParticipants(io, payload.conversationId, message);
  }
};

const notifyQueuedMessageFailed = (io, command, error) => {
  if (!io || !command?.senderId) return;

  io.to(`user:${command.senderId}`).emit("tin_nhan_gui_that_bai", {
    conversationId: command.conversationId,
    msgId: command.msgId,
    senderId: command.senderId,
    error: error?.message || "Không thể gửi tin nhắn",
    failedAt: new Date().toISOString(),
  });
};

const persistQueuedMessage = async (io, command = {}) => {
  const savedMessage = await MessageService.sendMessage({
    msgId: command.msgId,
    conversationId: command.conversationId,
    senderId: command.senderId,
    content: command.content,
    type: command.type,
    size: command.size,
    replyToMsgId: command.replyToMsgId,
    pollQuestion: command.pollQuestion,
    pollMultipleChoice: command.pollMultipleChoice,
    pollOptions: command.pollOptions,
    systemMeta: command.systemMeta,
    skipAccountStatusCheck: !queuedAccountStatusCheckEnabled,
  });

  await publishCreatedOrFallback(
    io,
    {
      conversationId: command.conversationId,
      msgId: savedMessage.msg_id,
      senderId: command.senderId,
      createdAt: savedMessage.createdAt || new Date().toISOString(),
    },
    savedMessage,
  );

  if (command.type === "poll" && command.pollQuestion) {
    const sysMsg = await MessageService.sendMessage({
      conversationId: command.conversationId,
      senderId: command.senderId,
      content: `${savedMessage.sender_name} đã tạo cuộc bình chọn: ${command.pollQuestion}`,
      type: "system_poll",
      skipAccountStatusCheck: true,
    });

    await publishCreatedOrFallback(
      io,
      {
        conversationId: command.conversationId,
        msgId: sysMsg.msg_id,
        senderId: command.senderId,
        createdAt: sysMsg.createdAt || new Date().toISOString(),
      },
      sysMsg,
    );
  }
};

const initChatMessageCommandConsumer = async (channel, io) => {
  await assertTopology(channel);

  const prefetch = parsePositiveInt(
    process.env.CHAT_MESSAGE_COMMAND_PREFETCH,
    50,
  );
  await channel.prefetch(prefetch);

  await channel.consume(
    QUEUES.MESSAGE_COMMAND,
    async (message) => {
      if (!message) return;

      const payload = safeParse(message.content);
      if (!payload) {
        channel.ack(message);
        return;
      }

      try {
        await persistQueuedMessage(io, payload);
        channel.ack(message);
      } catch (error) {
        console.error(
          "[chat-command] Failed processing queued message:",
          error?.message || error,
        );
        notifyQueuedMessageFailed(io, payload, error);
        channel.nack(message, false, false);
      }
    },
    { noAck: false },
  );

  console.log(
    ` [✓] ChatMessageCommandConsumer: ${QUEUES.MESSAGE_COMMAND} ready`,
  );
};

module.exports = {
  initChatMessageCommandConsumer,
};
