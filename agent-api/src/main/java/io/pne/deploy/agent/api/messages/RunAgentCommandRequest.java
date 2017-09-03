package io.pne.deploy.agent.api.messages;

import io.pne.deploy.agent.api.command.AgentCommand;

public class RunAgentCommandRequest implements IAgentServerMessage {

    public final String       agentId;
    public final String       commandId;
    public final AgentCommand command;

    public RunAgentCommandRequest(String agentId, String commandId, AgentCommand command) {
        this.agentId = agentId;
        this.commandId = commandId;
        this.command = command;
    }

    @Override
    public String toString() {
        return "RunAgentCommandRequest{" +
                "agentId='" + agentId + '\'' +
                ", commandId='" + commandId + '\'' +
                ", command=" + command +
                '}';
    }
}
