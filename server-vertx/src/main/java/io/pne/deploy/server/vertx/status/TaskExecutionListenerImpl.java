package io.pne.deploy.server.vertx.status;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.vertx.status.model.TaskStatus;

import java.util.concurrent.atomic.AtomicReference;

public class TaskExecutionListenerImpl implements ITaskExecutionListener {

    private final AtomicReference<Task>                    taskRef                 = new AtomicReference<>();
    private final AtomicReference<TaskCommand>             taskCommand             = new AtomicReference<>();
    private final AtomicReference<String>                  agentId                 = new AtomicReference<>();
    private final AtomicReference<String>                  commandId               = new AtomicReference<>();
    private final AtomicReference<RunAgentCommandLog>      runAgentCommandLog      = new AtomicReference<>();
    private final AtomicReference<RunAgentCommandResponse> runAgentCommandResponse = new AtomicReference<>();
    private final AtomicReference<RunAgentCommandRequest>  runAgentCommandRequest = new AtomicReference<>();
    private final AtomicReference<TaskStatus.CommandError> commandError = new AtomicReference<>();

    @Override
    public void onRunTask(Task aTask) {
        taskRef.set(aTask);
    }

    @Override
    public void onCommand(TaskCommand aCommand) {
        taskCommand.set(aCommand);
    }

    @Override
    public void onAgent(String aAgentId) {
        agentId.set(aAgentId);
    }

    @Override
    public void onCommandError(TaskCommand aCommand, String aAgentId, AgentCommandException aError) {
        commandError.set(TaskStatus.CommandError.builder()
                .command(aCommand)
                .agentId(aAgentId)
                .error(aError)
                .build());
    }

    @Override
    public void onCommandId(String aCommandId) {
        commandId.set(aCommandId);
    }

    @Override
    public void onSendingCommand(RunAgentCommandRequest aCommandRequest) {
        runAgentCommandRequest.set(aCommandRequest);
    }

    @Override
    public void onCommandResponse(RunAgentCommandResponse aResponse) {
        runAgentCommandResponse.set(aResponse);
    }

    @Override
    public void onCommandLog(RunAgentCommandLog aMessage) {
        runAgentCommandLog.set(aMessage);
    }

    public TaskStatus getTaskStatus() {
        return TaskStatus.builder()
                .task                    ( taskRef.get()                 )
                .taskCommand             ( taskCommand.get()             )
                .commandId               ( commandId.get()               )
                .agentId                 ( agentId.get()                 )
                .runAgentCommandRequest  ( runAgentCommandRequest.get()  )
                .runAgentCommandLog      ( runAgentCommandLog.get()      )
                .runAgentCommandResponse ( runAgentCommandResponse.get() )
                .commandError            ( commandError.get()            )
                .build();
    }
}
