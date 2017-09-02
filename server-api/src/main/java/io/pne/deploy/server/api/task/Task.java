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

    public Task(@Nonnull TaskId aId, @Nonnull TaskParameters aParameters, @Nonnull List<TaskCommand> aCommands) {
        id          = aId;
        commands    = aCommands;
        parameters  = aParameters;
    }
}
