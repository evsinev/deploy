package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;


public class TaskId {

    private final String id;

    private TaskId(String aId) {
        id = aId;
    }

    public static TaskId generateTaskId() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        return new TaskId(format.format(new Date()));
    }

    @Override @Nonnull
    public String toString() {
        return id;
    }


}
