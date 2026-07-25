package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 *
 */
public class Task {

    @Nonnull public final TaskId            id;
    @Nonnull public final List<TaskCommand>     commands;
    @Nonnull public final TaskParameters parameters;

    @Nonnull public final String taskLine;
    public final int    issueId;

    @Nullable
    public final TaskDiff diff;

    public Task(@Nonnull TaskId aId, @Nonnull TaskParameters aParameters, @Nonnull List<TaskCommand> aCommands, @Nonnull String aTaskLine, int aIssueId, @Nullable TaskDiff aDiff) {
        id          = aId;
        commands    = aCommands;
        parameters  = aParameters;
        taskLine    = aTaskLine;
        issueId     = aIssueId;
        diff        = aDiff;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", commands=" + commands +
                ", parameters=" + parameters +
                ", diff=" + diff +
                '}';
    }
}
