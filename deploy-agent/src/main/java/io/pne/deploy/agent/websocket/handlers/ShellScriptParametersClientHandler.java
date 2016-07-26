package io.pne.deploy.agent.websocket.handlers;

import io.pne.deploy.agent.tasks.ITaskService;
import io.pne.deploy.agent.tasks.impl.TaskContextImpl;
import io.pne.deploy.agent.websocket.IClientHandler;
import io.pne.deploy.agent.websocket.IClientHandlerContext;
import io.pne.deploy.agent.websocket.IMessageSender;
import io.pne.deploy.api.tasks.ShellScriptParameters;

public class ShellScriptParametersClientHandler implements IClientHandler<ShellScriptParameters> {

    private final IMessageSender sender;
    private final ITaskService taskService;

    public ShellScriptParametersClientHandler(ITaskService taskService, IMessageSender aSender) {
        this.taskService = taskService;
        sender = aSender;
    }

    @Override
    public void handle(ShellScriptParameters aMessage, IClientHandlerContext aContext) {
        taskService.runScript(aMessage, new TaskContextImpl(aMessage.taskId(), sender));
    }
}
