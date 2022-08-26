package io.pne.deploy.server.service.impl;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
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
    public void onRunTask(Task aTask) {
        LOG.debug("onRunTask({})", aTask);
    }

    @Override
    public void onCommand(TaskCommand aCommand) {
        LOG.debug("onCommand({})", aCommand);
    }

    @Override
    public void onAgent(String aAgentId) {
        LOG.debug("onAgent({})", aAgentId);
    }

    @Override
    public void onCommandError(TaskCommand aCommand, String aAgentId, AgentCommandException aError) {
        LOG.debug("onCommandError({}, {}, {})", aCommand, aAgentId, aError);

    }

    @Override
    public void onCommandId(String aCommandId) {
        LOG.debug("onCommandId({})", aCommandId);
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
}
