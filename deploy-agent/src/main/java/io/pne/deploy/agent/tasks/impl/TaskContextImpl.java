package io.pne.deploy.agent.tasks.impl;

import io.pne.deploy.agent.tasks.ITaskContext;
import io.pne.deploy.agent.websocket.IMessageSender;
import io.pne.deploy.api.tasks.ImmutableShellScriptLog;
import io.pne.deploy.api.tasks.ShellScriptResult;

public class TaskContextImpl implements ITaskContext {

    String         taskId;
    IMessageSender messageSender;

    public TaskContextImpl(String aTaskId, IMessageSender aSender) {
        taskId = aTaskId;
        messageSender = aSender;
    }

    @Override
    public void log(String aLine) {
        messageSender.enqueue(ImmutableShellScriptLog.builder()
                .taskId(taskId)
                .message(aLine)
                .build());
    }

    @Override
    public void sendResultToServer(ShellScriptResult aResult) {
        messageSender.enqueue(aResult);
    }
}
