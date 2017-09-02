package io.pne.deploy.agent.api.exceptions;

import io.pne.deploy.agent.api.command.AgentCommandId;

public class AgentCommandException extends Exception {

    private final AgentCommandId id;

    public AgentCommandException(AgentCommandId aId, String message) {
        super(message);
        id = aId;
    }

    public AgentCommandException(AgentCommandId aId, String message, Throwable cause) {
        super(message, cause);
        id = aId;
    }

    public AgentCommandId getCommandId() {
        return id;
    }
}
