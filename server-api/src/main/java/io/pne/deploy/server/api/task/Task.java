package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import java.util.List;

/**
 *
 */
public class Task {

    @Nonnull public final TaskId            id;
    @Nonnull public final List<TaskCommand>     commands;
    @Nonnull public final TaskParameters    parameters;

    @Nonnull public final String taskLine;
    public final int    issueId;

    public Task(@Nonnull TaskId aId, @Nonnull TaskParameters aParameters, @Nonnull List<TaskCommand> aCommands, @Nonnull String aTaskLine, int aIssueId) {
        id          = aId;
        commands    = aCommands;
        parameters  = aParameters;
        taskLine    = aTaskLine;
        issueId     = aIssueId;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", commands=" + commands +
                ", parameters=" + parameters +
                '}';
    }
}
