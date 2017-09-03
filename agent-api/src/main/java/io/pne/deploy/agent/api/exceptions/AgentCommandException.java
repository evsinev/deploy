package io.pne.deploy.agent.api.exceptions;

public class AgentCommandException extends Exception {

    public AgentCommandException(String message) {
        super(message);
    }

    public AgentCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
