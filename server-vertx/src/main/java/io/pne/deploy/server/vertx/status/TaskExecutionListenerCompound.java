package io.pne.deploy.server.vertx.status;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

public class TaskExecutionListenerCompound implements ITaskExecutionListener {

    private static final Logger LOG = getLogger(TaskExecutionListenerCompound.class);

    private final List<ITaskExecutionListener> listeners;

    public TaskExecutionListenerCompound(ITaskExecutionListener ... aListeners) {
        listeners = new ArrayList<>();
        for (ITaskExecutionListener listener : aListeners) {
            if(listener != null) {
                listeners.add(listener);
            }
        }
        LOG.info("Listeners are:");
        listeners.forEach(listener -> LOG.info("    {}", listener.getClass().getSimpleName()));
    }

    @Override
    public void onTaskStart(Task aTask) {
        execute("onTaskStart", (listener) -> listener.onTaskStart(aTask));
    }

    @Override
    public void onTaskSuccess(Task aTask) {
        execute("onTaskSuccess", (listener) -> listener.onTaskSuccess(aTask));
    }

    @Override
    public void onTaskError(Task aTask, Exception aException) {
        execute("onTaskError", (listener) -> listener.onTaskError(aTask, aException));

    }

    @Override
    public void onCommandStart(Task aTask, TaskCommand aCommand) {
        execute("onCommandStart", (listener) -> listener.onCommandStart(aTask, aCommand));
    }

    @Override
    public void onCommandSuccess(Task aTask, TaskCommand aCommand) {
        execute("onCommandSuccess", (listener) -> listener.onCommandSuccess(aTask, aCommand));
    }

    @Override
    public void onCommandError(Task aTask, TaskCommand aCommand, Exception e) {
        execute("onCommandError", (listener) -> listener.onCommandError(aTask, aCommand, e));
    }

    @Override
    public void onAgentCommandStart(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aCommandRequest) {
        execute("onAgentCommandStart", (listener) -> listener.onAgentCommandStart(aTask, aCommand, aCommandRequest));
    }

    @Override
    public void onAgentCommandSuccess(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand) {
        execute("onAgentCommandSuccess", (listener) -> listener.onAgentCommandSuccess(aTask, aCommand, aAgentCommand));
    }

    @Override
    public void onAgentCommandError(Task aTask, TaskCommand aCommand, RunAgentCommandRequest aAgentCommand, Exception e) {
        execute("onAgentCommandError", (listener) -> listener.onAgentCommandError(aTask, aCommand, aAgentCommand, e));
    }

    @Override
    public void onSendingCommand(RunAgentCommandRequest aCommandRequest) {
        execute("onSendingCommand", (listener) -> listener.onSendingCommand(aCommandRequest));
    }

    @Override
    public void onCommandResponse(RunAgentCommandResponse aResponse) {
        execute("onCommandResponse", (listener) -> listener.onCommandResponse(aResponse));
    }

    @Override
    public void onCommandLog(RunAgentCommandLog aMessage) {
        execute("onCommandLog", (listener) -> listener.onCommandLog(aMessage));
    }

    private void execute(String aName, Consumer<ITaskExecutionListener> aConsumer) {
        LOG.trace("Invoking {}", aName);
        for (ITaskExecutionListener listener : listeners) {
            LOG.trace("Invoking {}.{}", listener.getClass().getSimpleName(), aName);
            try {
                aConsumer.accept(listener);
            } catch (Exception e) {
                LOG.error("Cannot invoke {}.{}", listener.getClass().getSimpleName(), aName, e);
            }
        }
    }
}
