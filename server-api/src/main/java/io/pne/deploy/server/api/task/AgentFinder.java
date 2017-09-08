package io.pne.deploy.server.api.task;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;

@ParametersAreNonnullByDefault
public class AgentFinder {

    @Nonnull private final String[] ids;

    public AgentFinder(@Nonnull String[] id) {
        this.ids = id;
    }

    @Nonnull
    public static AgentFinder agentByName(String aName) {
        return new AgentFinder(aName.split(","));
    }

    @Nonnull
    public String[] getIds() {
        return ids;
    }

    @Override
    public String toString() {
        return "AgentFinder{" +
                "ids=" + Arrays.toString(ids) +
                '}';
    }
}
