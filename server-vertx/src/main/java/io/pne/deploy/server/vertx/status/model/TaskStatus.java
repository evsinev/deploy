package io.pne.deploy.server.vertx.status.model;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@FieldDefaults(level = PRIVATE, makeFinal = true)
public class TaskStatus {

    Task                    task;
    TaskCommand             taskCommand;
    String                  agentId;
    String                  commandId;
    RunAgentCommandLog      runAgentCommandLog;
    RunAgentCommandRequest  runAgentCommandRequest;
    RunAgentCommandResponse runAgentCommandResponse;
    CommandError            commandError;

    @Data
    @Builder
    @FieldDefaults(level = PRIVATE, makeFinal = true)
    public static class CommandError {
        TaskCommand           command;
        String                agentId;
        AgentCommandException error;
    }

}

