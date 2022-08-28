package io.pne.deploy.server.vertx.status;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.slf4j.LoggerFactory.getLogger;

public class TaskExecutionListenerQueue implements ITaskExecutionListener {

    private static final Logger LOG = getLogger(TaskExecutionListenerQueue.class);

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final ITaskExecutionListener listener;

    public TaskExecutionListenerQueue(ITaskExecutionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onTaskStart(Task aTask) {
        execute("onTaskStart", () -> listener.onTaskStart(aTask));
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        execute("onTaskSuccess", () -> listener.onTaskSuccess(aTask));
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        execute("onTaskError", () -> listener.onTaskError(aTask, aException));

    }

    @Override
    public void onCommandStart(Task aTask, TaskCommand aCommand) {
        execute("onCommandStart", () -> listener.onCommandStart(aTask, aCommand));
    }

    @Override
    public void onCommandSuccess(Task aTask, TaskCommand aCommand) {
        execute("onCommandSuccess", () -> listener.onCommandSuccess(aTask, aCommand));
    }

    @Override
    public void onCommandError(Task aTask, TaskCommand aCommand, Exception e) {
        execute("onCommandError", () -> listener.onCommandError(aTask, aCommand, e));
    }

    @Override
    public void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest) {
        execute("onAgentCommandStart", () -> listener.onAgentCommandStart(aTask, aCommand, aCommandRequest));
    }

    @Override
    public void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand) {
        execute("onAgentCommandSuccess", () -> listener.onAgentCommandSuccess(aTask, aCommand, aAgentCommand));
    }

    @Override
    public void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e) {
        execute("onAgentCommandError", () -> listener.onAgentCommandError(aTask, aCommand, aAgentCommand, e));
    }

    @Override
    public void onSendingCommand(RunAgentCommandRequest aCommandRequest) {
        execute("onSendingCommand", () -> listener.onSendingCommand(aCommandRequest));
    }

    @Override
    public void onCommandResponse(RunAgentCommandResponse aResponse) {
        execute("onCommandResponse", () -> listener.onCommandResponse(aResponse));
    }

    @Override
    public void onCommandLog(RunAgentCommandLog aMessage) {
        execute("onCommandLog", () -> listener.onCommandLog(aMessage));
    }

    private void execute(String aName, Runnable aRunnable) {
        LOG.trace("Scheduling {}", aName);
        executor.execute(() -> {
            try {
                LOG.trace("Executing {}", aName);
                aRunnable.run();
            } catch (Exception e) {
                LOG.error("error executing", e);
            }
        });
    }
}
