package io.pne.deploy.server.api;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;

public interface ITaskExecutionListener {


    void onRunTask(Task aTask);

    void onCommand(TaskCommand aCommand);

    void onAgent(String aAgentId);

    void onCommandError(TaskCommand aCommand, String aAgentId, AgentCommandException aError);

    void onCommandId(String aCommandId);

    void onSendingCommand(RunAgentCommandRequest aCommandRequest);

    void onCommandResponse(RunAgentCommandResponse aResponse);

    void onCommandLog(RunAgentCommandLog aMessage);
}
