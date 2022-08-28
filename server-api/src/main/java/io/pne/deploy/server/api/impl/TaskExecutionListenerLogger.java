package io.pne.deploy.server.api.impl;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class TaskExecutionListenerLogger implements ITaskExecutionListener {

    private static final Logger LOG = getLogger(TaskExecutionListenerLogger.class);

    @Override
    public void onTaskStart(Task aTask) {
        LOG.debug("onRunTask({})", aTask);
    }

    @Override
    public void onSendingCommand(RunAgentCommandRequest aCommandRequest) {
        LOG.debug("onSendingCommand({})", aCommandRequest);
    }

    @Override
    public void onCommandResponse(RunAgentCommandResponse aResponse) {
        LOG.debug("onCommandResponse({})", aResponse);
    }

    @Override
    public void onCommandLog(RunAgentCommandLog aMessage) {
        LOG.debug("onCommandLog({})", aMessage);
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        LOG.debug("onTaskSuccess({})", aTask);

    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        LOG.debug("onTaskError({})", aTask, aException);

    }

    @Override
    public void onCommandStart(Task aTask, TaskCommand aCommand) {
        LOG.debug("onCommandStart({}, {})", aTask, aCommand);
    }

    @Override
    public void onCommandSuccess(Task aTask, TaskCommand aCommand) {
        LOG.debug("onCommandSuccess({}, {})", aTask, aCommand);
    }

    @Override
    public void onCommandError(Task aTask, TaskCommand aCommand, Exception e) {
        LOG.debug("onCommandError({}, {})", aTask, aCommand, e);
    }

    @Override
    public void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest) {
        LOG.debug("onAgentCommandStart({}, {}, {})", aTask, aCommand, aCommandRequest);

    }

    @Override
    public void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand) {
        LOG.debug("onAgentCommandStart({}, {}, {})", aTask, aCommand, aAgentCommand);
    }

    @Override
    public void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e) {
        LOG.debug("onAgentCommandError({}, {}, {})", aTask, aCommand, aAgentCommand, e);
    }
}
