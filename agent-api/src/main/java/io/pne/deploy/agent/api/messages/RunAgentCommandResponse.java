package io.pne.deploy.agent.api.messages;

public class RunAgentCommandResponse implements IAgentClientMessage {

    public final String    commandId;
    public final Exception error;

    public RunAgentCommandResponse(String aCommandId) {
        error = null;
        commandId = aCommandId;
    }

    public RunAgentCommandResponse(String aCommandId, Exception error) {
        this.error = error;
        commandId = aCommandId;

    }

    @Override
    public String toString() {
        return "RunAgentCommandResponse{" +
                "commandId='" + commandId + '\'' +
                ", error=" + error +
                '}';
    }
}
