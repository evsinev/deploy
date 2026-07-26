package io.pne.deploy.agent.api.messages;

/** Agent -> server: system info the agent pushes right after each connect (version + JVM heap). */
public class AgentInfo implements IAgentClientMessage {

    public final String version;
    public final long   heapUsed;
    public final long   heapMax;

    public AgentInfo(String version, long heapUsed, long heapMax) {
        this.version  = version;
        this.heapUsed = heapUsed;
        this.heapMax  = heapMax;
    }

    @Override
    public String toString() {
        return "AgentInfo{version='" + version + "', heapUsed=" + heapUsed + ", heapMax=" + heapMax + "}";
    }
}
