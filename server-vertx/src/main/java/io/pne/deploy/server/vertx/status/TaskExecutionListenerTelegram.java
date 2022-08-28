package io.pne.deploy.server.vertx.status;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;

public class TaskExecutionListenerTelegram implements ITaskExecutionListener {

    private final ITelegramService telegramService;
    private final int              chatId;

    public TaskExecutionListenerTelegram(int aChatId, String aToken) {
        chatId = aChatId;
        telegramService = new TelegramServiceImpl(
                new TelegramHttpClientImpl(
                        aToken
                )
        );
    }

    @Override
    public void onTaskStart(Task aTask) {
        sendMessage("\uD83D\uDEEB " + aTask.taskLine); // 🛫
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        sendMessage("\uD83D\uDC4C " + aTask.taskLine); // 👌
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        sendMessage("\uD83D\uDD25 " + aTask.taskLine); // 🔥
    }

    @Override
    public void onCommandStart(Task aTask, TaskCommand aCommand) {

    }

    @Override
    public void onCommandSuccess(Task aTask, TaskCommand aCommand) {

    }

    @Override
    public void onCommandError(Task aTask, TaskCommand aCommand, Exception e) {
    }

    @Override
    public void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest) {

    }

    @Override
    public void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand) {

    }

    @Override
    public void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e) {

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

    private void sendMessage(String aMessage) {
        telegramService.sendMessage(TelegramMessageRequest.builder()
                .chatId(chatId)
                .text(aMessage)
                .build());
    }
}

