package io.pne.deploy.server.vertx.status.model;

import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class TaskState {

    public enum Type {
        RUNNING, SUCCESS, ERROR
    }

    Type   type;
    String errorMessage;

    public static TaskState taskSuccess() {
        return new TaskState(Type.SUCCESS, null);
    }

    public static TaskState taskRunning() {
        return new TaskState(Type.RUNNING, null);
    }

    public static TaskState taskError(String aError) {
        return new TaskState(Type.ERROR, aError);
    }


}
