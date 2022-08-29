package io.pne.deploy.server.vertx.status;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.vertx.status.telegram.TelegramMessagesStore;

public class TaskExecutionListenerTelegram implements ITaskExecutionListener {

    private final ITelegramService      telegramService;
    private final int                   chatId;
    private final TelegramMessagesStore store;

    public TaskExecutionListenerTelegram(int aChatId, String aToken) {
        chatId = aChatId;
        telegramService = new TelegramServiceImpl(
                new TelegramHttpClientImpl(
                        aToken
                )
        );
        store = new TelegramMessagesStore(telegramService);
    }

    @Override
    public void onTaskStart(Task aTask) {
        String text = "\uD83D\uDEEB " + aTask.taskLine; // 🛫
        TelegramMessage telegramMessage = sendMessage(text);
        store.addTask(aTask, telegramMessage,  text);
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        sendMessage("\uD83D\uDC4C " + aTask.taskLine); // 👌
        store.removeTask(aTask);
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        sendMessage("\uD83D\uDD25 " + aTask.taskLine); // 🔥
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

    private TelegramMessage sendMessage(String aMessage) {
        return telegramService.sendMessage(TelegramMessageRequest.builder()
                .chatId(chatId)
                .text(aMessage)
                .build());
    }
}

