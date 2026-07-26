package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.AgentInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-agent details for the dashboard "Agents" screen: IP + connect time + status are derived server-side, while
 * version + heap are pushed by the agent ({@link AgentInfo}) right after each connect. Disconnected agents are kept
 * (with a last-seen time) so the screen can show them as {@code DISCONNECTED}. In-memory only (not persisted).
 */
public class AgentRegistry {

    public enum Status { CONNECTED, DISCONNECTED }

    /** Immutable snapshot of one agent, for rendering. */
    public static final class AgentRecord {
        public final String agentId;
        public final String ip;
        public final long   connectedAtMs;
        public final long   lastSeenMs;
        public final Status status;
        public final String version;
        public final Long   heapUsed;
        public final Long   heapMax;

        public AgentRecord(String aAgentId, String aIp, long aConnectedAtMs, long aLastSeenMs, Status aStatus,
                           String aVersion, Long aHeapUsed, Long aHeapMax) {
            agentId       = aAgentId;
            ip            = aIp;
            connectedAtMs = aConnectedAtMs;
            lastSeenMs    = aLastSeenMs;
            status        = aStatus;
            version       = aVersion;
            heapUsed      = aHeapUsed;
            heapMax       = aHeapMax;
        }
    }

    private static final class Mutable {
        String agentId;
        String ip;
        long   connectedAtMs;
        long   lastSeenMs;
        Status status;
        String version;
        Long   heapUsed;
        Long   heapMax;
    }

    private final Map<String, Mutable> map = new HashMap<>();

    public synchronized void onConnect(String aAgentId, String aIp) {
        long now = System.currentTimeMillis();
        Mutable m = map.computeIfAbsent(aAgentId, id -> new Mutable());
        m.agentId       = aAgentId;
        m.ip            = aIp;
        m.connectedAtMs = now;
        m.lastSeenMs    = now;
        m.status        = Status.CONNECTED;
        // version/heap are kept from a previous connect until the fresh AgentInfo frame arrives (avoids a flicker).
    }

    public synchronized void onInfo(String aAgentId, AgentInfo aInfo) {
        Mutable m = map.computeIfAbsent(aAgentId, id -> new Mutable());
        m.agentId    = aAgentId;
        m.version    = aInfo.version;
        m.heapUsed   = aInfo.heapUsed;
        m.heapMax    = aInfo.heapMax;
        m.lastSeenMs = System.currentTimeMillis();
        if (m.status == null) {
            m.status = Status.CONNECTED;
        }
    }

    public synchronized void onDisconnect(String aAgentId) {
        Mutable m = map.get(aAgentId);
        if (m != null) {
            m.status     = Status.DISCONNECTED;
            m.lastSeenMs = System.currentTimeMillis();
        }
    }

    /** Connected agents first, then by id. */
    public synchronized List<AgentRecord> snapshot() {
        List<AgentRecord> list = new ArrayList<>(map.size());
        for (Mutable m : map.values()) {
            list.add(new AgentRecord(m.agentId, m.ip, m.connectedAtMs, m.lastSeenMs, m.status,
                    m.version, m.heapUsed, m.heapMax));
        }
        list.sort(Comparator
                .comparingInt((AgentRecord r) -> r.status == Status.CONNECTED ? 0 : 1)
                .thenComparing(r -> r.agentId == null ? "" : r.agentId));
        return list;
    }
}
