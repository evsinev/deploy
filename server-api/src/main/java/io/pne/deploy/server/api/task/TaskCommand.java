package io.pne.deploy.server.api.task;

import io.pne.deploy.agent.api.command.AgentCommand;

import javax.annotation.Nonnull;

public class TaskCommand {

    @Nonnull public final AgentFinder agents;
    @Nonnull public final AgentCommand command;

    public TaskCommand(@Nonnull AgentFinder agents, @Nonnull AgentCommand command) {
        this.agents = agents;
        this.command = command;
    }

    @Override
    public String toString() {
        return "TaskCommand{" +
                "agents=" + agents +
                ", command=" + command +
                '}';
    }
}
