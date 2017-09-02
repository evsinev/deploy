package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class AgentFinder {

    @Nonnull private final String[] ids;

    public AgentFinder(@Nonnull String[] id) {
        this.ids = id;
    }

    @Nonnull
    public static AgentFinder agentByName(String aName) {
        return new AgentFinder(new String[]{aName});
    }

    @Nonnull
    public String[] getIds() {
        return ids;
    }
}
