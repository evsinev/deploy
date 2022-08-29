package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;


public class TaskId {

    private final String id;

    private TaskId(String aId) {
        id = aId;
    }

    public static TaskId generateTaskId() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
        return new TaskId(format.format(new Date()));
    }

    public TaskId addRedmineIssueId(int aIssueId) {
        return new TaskId(id + "-" + aIssueId);
    }

    @Override @Nonnull
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskId taskId = (TaskId) o;
        return Objects.equals(id, taskId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
