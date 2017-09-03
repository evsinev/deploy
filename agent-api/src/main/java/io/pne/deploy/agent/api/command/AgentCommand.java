package io.pne.deploy.agent.api.command;

import io.pne.deploy.agent.api.messages.IAgentServerMessage;

import javax.annotation.Nonnull;

import java.util.List;

public class AgentCommand implements IAgentServerMessage {

     @Nonnull public final AgentCommandParameters parameters;
     @Nonnull public final AgentCommandType       type;
     @Nonnull public final String                 name;
     @Nonnull public final List<String>           arguments;

    public AgentCommand(@Nonnull AgentCommandParameters parameters, @Nonnull AgentCommandType type, @Nonnull String name, @Nonnull List<String> arguments) {
        this.parameters = parameters;
        this.type = type;
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "AgentCommand{" +
                "  type=" + type +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                ", parameters=" + parameters +
                '}';
    }
}
