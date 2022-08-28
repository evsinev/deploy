package io.pne.deploy.server.vertx.status.model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder(toBuilder = true)
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class TaskStatus {

    String              taskId;
    int                 issueId;
    String              taskLine;
    List<CommandStatus> commands;
    TaskState           taskState;

}

