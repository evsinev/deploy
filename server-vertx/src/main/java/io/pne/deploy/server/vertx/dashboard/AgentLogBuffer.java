package io.pne.deploy.server.vertx.dashboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A small thread-safe ring buffer of the most recent agent command-log lines. Written from the
 * Vert.x event loop (via the server listener) and read from the SSE timer thread, so all access is
 * synchronized. Connect/disconnect are not agent messages, so they never land here.
 */
public class AgentLogBuffer {

    private final int          capacity;
    private final Deque<LogLine> lines = new ArrayDeque<>();

    public AgentLogBuffer(int aCapacity) {
        this.capacity = Math.max(1, aCapacity);
    }

    public synchronized void add(String aCommandId, String aMessage) {
        lines.addLast(new LogLine(System.currentTimeMillis(), aCommandId, aMessage));
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /** Up to {@code aMax} most-recent lines, newest first. */
    public synchronized List<LogLine> snapshot(int aMax) {
        List<LogLine> out = new ArrayList<>(Math.min(aMax, lines.size()));
        var it = lines.descendingIterator();
        while (it.hasNext() && out.size() < aMax) {
            out.add(it.next());
        }
        return out;
    }

    /** One captured log line. {@code commandId} correlates lines of one command; there is no agent id. */
    public record LogLine(long epochMs, String commandId, String message) {
    }
}
