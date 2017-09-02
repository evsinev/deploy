package io.pne.deploy.agent.api.command;

import com.sun.istack.internal.NotNull;

import java.util.UUID;

public class AgentCommandId {

    private final String id;

    private AgentCommandId(String aId) {
        id = aId;
    }

    public static AgentCommandId generateCommandId() {
        return new AgentCommandId(UUID.randomUUID().toString());
    }

    @NotNull public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "AgentCommandId{" +
                "id='" + id + '\'' +
                '}';
    }
}
