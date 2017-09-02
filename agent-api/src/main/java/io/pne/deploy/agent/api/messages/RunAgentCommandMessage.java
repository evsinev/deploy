package io.pne.deploy.agent.api.messages;

import io.pne.deploy.agent.api.command.AgentCommand;

public class RunAgentCommandMessage implements IAgentServerMessage {

    public final String       agentId;
    public final AgentCommand command;

    public RunAgentCommandMessage(String agentId, AgentCommand command) {
        this.agentId = agentId;
        this.command = command;
    }

    @Override
    public String toString() {
        return "RunAgentCommandMessage{" +
                "agentId='" + agentId + '\'' +
                ", command=" + command +
                '}';
    }
}
