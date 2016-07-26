package io.pne.deploy.agent.tasks.impl;

import io.pne.deploy.agent.tasks.ITaskContext;
import io.pne.deploy.agent.websocket.IMessageSender;
import io.pne.deploy.api.tasks.ShellScriptResult;

public class TaskContextImpl implements ITaskContext {

    String         ticketId;
    IMessageSender messageSender;

    public TaskContextImpl(String aTicketId, IMessageSender aSender) {
        ticketId = aTicketId;
        messageSender = aSender;
    }

    @Override
    public void log(String aLine) {

//        messageSender.enqueue();
    }

    @Override
    public void sendResultToServer(ShellScriptResult aResult) {
        messageSender.enqueue(aResult);
    }
}
