package io.pne.deploy.server.vertx.status;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.vertx.status.model.TaskState;
import io.pne.deploy.server.vertx.status.model.TaskStatus;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class TaskExecutionListenerStatus implements ITaskExecutionListener {

    private final Consumer<TaskStatus>        consumer;
    private final AtomicReference<TaskStatus> statusRef = new AtomicReference<>(TaskStatus.builder().build());

    public TaskExecutionListenerStatus(Consumer<TaskStatus> aConsumer) {
        consumer = aConsumer;
    }

    @Override
    public void onTaskStart(Task aTask) {
        updateTask((oldTask) -> TaskStatus.builder()
                .taskId(aTask.id.toString())
                .issueId(aTask.issueId)
                .taskLine(aTask.taskLine)
                .taskState(TaskState.taskRunning())
                .build());
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        updateTask(old -> old.toBuilder().taskState(TaskState.taskSuccess()).build());
    }

    private void updateTask(Function<TaskStatus, TaskStatus> aUpdate) {
        statusRef.set(aUpdate.apply(statusRef.get()));
        consumer.accept(statusRef.get());
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        updateTask(old -> old.toBuilder().taskState(TaskState.taskError(aException.getMessage())).build());
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

}
