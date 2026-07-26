package io.pne.deploy.server.vertx.dashboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A small thread-safe ring buffer of the most recent agent command-log lines. Written from the
 * Vert.x event loop (via the server listener) and read from the SSE timer thread, so all access is
 * synchronized. Connect/disconnect are not agent messages, so they never land here.
 *
 * <p>Each line carries a monotonically increasing {@code seq} so the SSE tail can push only the
 * lines appended since a given cursor. All reads return lines <em>oldest-first</em> (newest last),
 * matching how the dashboard appends them at the bottom of the log view.
 */
public class AgentLogBuffer {

    private final int            capacity;
    private final Deque<LogLine> lines = new ArrayDeque<>();
    private long                 nextSeq = 0;

    public AgentLogBuffer(int aCapacity) {
        this.capacity = Math.max(1, aCapacity);
    }

    public synchronized void add(String aAgentId, String aCommandId, String aMessage) {
        lines.addLast(new LogLine(nextSeq++, System.currentTimeMillis(), aAgentId, aCommandId, aMessage));
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /** Up to {@code aMax} most-recent lines, oldest-first (newest last). */
    public synchronized List<LogLine> tail(int aMax) {
        List<LogLine> out = new ArrayList<>(Math.min(aMax, lines.size()));
        var it = lines.descendingIterator();
        while (it.hasNext() && out.size() < aMax) {
            out.add(it.next()); // newest-first while collecting
        }
        Collections.reverse(out); // hand back oldest-first
        return out;
    }

    /** Lines appended strictly after {@code aAfterSeq}, oldest-first; empty when nothing is newer. */
    public synchronized List<LogLine> since(long aAfterSeq) {
        List<LogLine> out = new ArrayList<>();
        for (LogLine line : lines) { // ArrayDeque iterates head->tail = oldest->newest
            if (line.seq() > aAfterSeq) {
                out.add(line);
            }
        }
        return out;
    }

    /** Seq of the newest buffered line, or {@code -1} when empty; use as the initial SSE cursor. */
    public synchronized long lastSeq() {
        return nextSeq - 1;
    }

    /** One captured log line. {@code agentId} is the source agent; {@code commandId} correlates lines of one command. */
    public record LogLine(long seq, long epochMs, String agentId, String commandId, String message) {
    }
}
