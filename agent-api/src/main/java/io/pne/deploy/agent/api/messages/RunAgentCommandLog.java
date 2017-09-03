package io.pne.deploy.agent.api.messages;

public class RunAgentCommandLog implements IAgentClientMessage {

    public final String commandId;
    public final String message;

    public RunAgentCommandLog(String commandId, String message) {
        this.commandId = commandId;
        this.message = message;
    }

    @Override
    public String toString() {
        return "RunAgentCommandLog{" +
                "commandId='" + commandId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
