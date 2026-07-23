package io.pne.deploy.server.vertx.status;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.client.redmine.remote.impl.TelegramClient;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.vertx.status.telegram.TelegramMessagesStore;

public class TaskExecutionListenerTelegram implements ITaskExecutionListener {

    private final TelegramClient        telegram;
    private final long                  chatId;
    private final TelegramMessagesStore store;

    public TaskExecutionListenerTelegram(long aChatId, String aToken) {
        chatId   = aChatId;
        telegram = new TelegramClient(aToken);
        store    = new TelegramMessagesStore(telegram, aChatId);
    }

    @Override
    public void onTaskStart(Task aTask) {
        String text = "🛫 " + aTask.taskLine; // 🛫
        long messageId = sendMessage(text);
        store.addTask(aTask, messageId, text);
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        sendMessage("👌 " + aTask.taskLine); // 👌
        store.removeTask(aTask);
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        sendMessage("🔥 " + aTask.taskLine); // 🔥
        store.removeTask(aTask);
    }

    @Override
    public void onCommandStart(Task aTask, TaskCommand aCommand) {
        store.updateTask(aTask, " " + aCommand.command.name + "...");
    }

    @Override
    public void onCommandSuccess(Task aTask, TaskCommand aCommand) {
        store.updateTask(aTask, " " + aCommand.command.name + " ✔️");
    }

    @Override
    public void onCommandError(Task aTask, TaskCommand aCommand, Exception e) {
        store.updateTask(aTask, " " + aCommand.command.name + " ERROR " + e);
    }

    @Override
    public void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest) {
        store.updateTask(aTask, "    " + aCommandRequest.agentId + " ...");
    }

    @Override
    public void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand) {
        store.updateTask(aTask, "    " + aAgentCommand.agentId + " OK");
    }

    @Override
    public void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e) {
        store.updateTask(aTask, "    " + aAgentCommand.agentId + " ERROR " + e);
    }

    @Override
    public void onSendingCommand(RunAgentCommandRequest aCommandRequest) {

    }

    @Override
    public void onCommandResponse(RunAgentCommandResponse aResponse) {

    }

    @Override
    public void onCommandLog(RunAgentCommandLog aMessage) {

    }

    private long sendMessage(String aMessage) {
        return telegram.sendMessage(chatId, aMessage, null);
    }
}
