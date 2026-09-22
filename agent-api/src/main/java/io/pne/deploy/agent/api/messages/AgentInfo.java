package io.pne.deploy.agent.api.messages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Agent -> server: system info the agent pushes right after each connect (version, JVM heap, capabilities). */
public class AgentInfo implements IAgentClientMessage {

    /** Capability token for the first version of the typed step plan protocol. */
    public static final String CAPABILITY_STEPS_1 = "STEPS/1";

    public final String version;
    public final long   heapUsed;
    public final long   heapMax;

    /** What this agent can execute. {@code null} from agents released before capabilities existed: shell only. */
    @Nullable public final List<String> capabilities;

    public AgentInfo(String version, long heapUsed, long heapMax) {
        this(version, heapUsed, heapMax, null);
    }

    public AgentInfo(String version, long heapUsed, long heapMax, @Nullable List<String> capabilities) {
        this.version      = version;
        this.heapUsed     = heapUsed;
        this.heapMax      = heapMax;
        this.capabilities = capabilities;
    }

    @Nonnull
    public List<String> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : capabilities;
    }

    public boolean hasCapability(String aCapability) {
        return getCapabilities().contains(aCapability);
    }

    @Override
    public String toString() {
        return "AgentInfo{version='" + version + "', heapUsed=" + heapUsed + ", heapMax=" + heapMax
                + ", capabilities=" + getCapabilities() + "}";
    }
}
