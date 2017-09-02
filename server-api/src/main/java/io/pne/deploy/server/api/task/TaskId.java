package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import java.util.UUID;


public class TaskId {

    private final String id;

    private TaskId(String aId) {
        id = aId;
    }

    public static TaskId generateTaskId() {
        return new TaskId(UUID.randomUUID().toString());
    }

    @Override @Nonnull
    public String toString() {
        return id;
    }


}
