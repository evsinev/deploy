package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.AgentInfo;

import java.util.ArrayList;
import java.util.Collections;
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
        /** What the agent said it can run. Empty for an agent released before it said anything. */
        public final List<String> capabilities;

        public AgentRecord(String aAgentId, String aIp, long aConnectedAtMs, long aLastSeenMs, Status aStatus,
                           String aVersion, Long aHeapUsed, Long aHeapMax) {
            this(aAgentId, aIp, aConnectedAtMs, aLastSeenMs, aStatus, aVersion, aHeapUsed, aHeapMax,
                    Collections.emptyList());
        }

        public AgentRecord(String aAgentId, String aIp, long aConnectedAtMs, long aLastSeenMs, Status aStatus,
                           String aVersion, Long aHeapUsed, Long aHeapMax, List<String> aCapabilities) {
            agentId       = aAgentId;
            ip            = aIp;
            connectedAtMs = aConnectedAtMs;
            lastSeenMs    = aLastSeenMs;
            status        = aStatus;
            version       = aVersion;
            heapUsed      = aHeapUsed;
            heapMax       = aHeapMax;
            capabilities  = aCapabilities == null ? Collections.emptyList() : aCapabilities;
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
        List<String> capabilities;
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
        // Capabilities are not: what the previous connection could do says nothing about this one, and acting on
        // a stale answer would mean sending a plan to an agent that cannot run it.
        m.capabilities  = null;
    }

    /** How long to give a freshly connected agent to say what it can do before concluding that it cannot. */
    public static final long CAPABILITY_WAIT_MS = 5_000;

    public synchronized void onInfo(String aAgentId, AgentInfo aInfo) {
        Mutable m = map.computeIfAbsent(aAgentId, id -> new Mutable());
        m.agentId    = aAgentId;
        m.version      = aInfo.version;
        m.heapUsed     = aInfo.heapUsed;
        m.heapMax      = aInfo.heapMax;
        m.capabilities = aInfo.getCapabilities();
        m.lastSeenMs = System.currentTimeMillis();
        if (m.status == null) {
            m.status = Status.CONNECTED;
        }
        notifyAll();
    }

    /**
     * Whether an agent said it can do something.
     *
     * <p>An agent that has not said so is treated as unable, so a server that has moved on does not send a
     * connected but older agent something it would not understand.
     */
    public synchronized boolean hasCapability(String aAgentId, String aCapability) {
        Mutable m = map.get(aAgentId);
        return m != null && m.capabilities != null && m.capabilities.contains(aCapability);
    }

    /**
     * The same, but gives an agent that has only just connected a moment to answer.
     *
     * <p>An agent says what it can do immediately after connecting, but that message still has to arrive. A
     * deploy started in that moment - after a restart or a network blip - would otherwise be told the agent
     * cannot run plans when in fact nobody had asked it yet.
     */
    public synchronized boolean awaitCapability(String aAgentId, String aCapability, long aTimeoutMs) {
        long deadline = System.currentTimeMillis() + aTimeoutMs;
        while (!hasCapability(aAgentId, aCapability)) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            Mutable m = map.get(aAgentId);
            if (m != null && m.capabilities != null) {
                return false;       // it has answered, and this is not among the things it can do
            }
            try {
                wait(remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** What an agent said it can do, for a message that has to explain why something was not sent. */
    public synchronized String describe(String aAgentId) {
        Mutable m = map.get(aAgentId);
        if (m == null) {
            return "agent " + aAgentId + " has never connected";
        }
        return "agent " + aAgentId + " (version " + m.version + ", " + m.status + ") reports "
                + (m.capabilities == null || m.capabilities.isEmpty() ? "no capabilities" : m.capabilities);
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
                    m.version, m.heapUsed, m.heapMax, m.capabilities));
        }
        list.sort(Comparator
                .comparingInt((AgentRecord r) -> r.status == Status.CONNECTED ? 0 : 1)
                .thenComparing(r -> r.agentId == null ? "" : r.agentId));
        return list;
    }
}
