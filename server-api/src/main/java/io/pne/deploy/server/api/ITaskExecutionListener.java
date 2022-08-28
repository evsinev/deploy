package io.pne.deploy.server.api;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;

public interface ITaskExecutionListener {

    //region Task
    void onTaskStart(Task aTask);

    void onTaskSuccess(Task aTask);

    void onTaskError(Task aTask, Exception aException);
    //endregion Task

    //region Command
    void onCommandStart(Task aTask, TaskCommand aCommand);

    void onCommandSuccess(Task aTask, TaskCommand aCommand);

    void onCommandError(Task aTask, TaskCommand aCommand, Exception e);
    //endregion

    //region Agent Command
    void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest);

    void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand);

    void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e);
    //endregion

    //region Low level communication
    void onSendingCommand(RunAgentCommandRequest aCommandRequest);

    void onCommandResponse(RunAgentCommandResponse aResponse);

    void onCommandLog(RunAgentCommandLog aMessage);
    //endregion

}
