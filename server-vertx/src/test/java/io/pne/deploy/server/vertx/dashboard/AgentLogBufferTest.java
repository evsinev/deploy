package io.pne.deploy.server.vertx.dashboard;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentLogBufferTest {

    @Test
    public void keepsMostRecentUpToCapacityOldestFirst() {
        AgentLogBuffer buffer = new AgentLogBuffer(3);
        buffer.add("a", "c","one");
        buffer.add("a", "c","two");
        buffer.add("a", "c","three");
        buffer.add("a", "c","four"); // evicts "one"

        List<AgentLogBuffer.LogLine> tail = buffer.tail(10);
        assertEquals(3, tail.size());
        assertEquals("two", tail.get(0).message());   // oldest-first, "one" evicted
        assertEquals("three", tail.get(1).message());
        assertEquals("four", tail.get(2).message());  // newest last
    }

    @Test
    public void tailRespectsMaxAndKeepsNewest() {
        AgentLogBuffer buffer = new AgentLogBuffer(100);
        for (int i = 0; i < 10; i++) {
            buffer.add("a", "c","m" + i);
        }
        List<AgentLogBuffer.LogLine> tail = buffer.tail(3);
        assertEquals(3, tail.size());
        assertEquals("m7", tail.get(0).message()); // 3 newest, oldest-first
        assertEquals("m8", tail.get(1).message());
        assertEquals("m9", tail.get(2).message());
    }

    @Test
    public void sinceReturnsOnlyLinesAfterCursorOldestFirst() {
        AgentLogBuffer buffer = new AgentLogBuffer(100);
        buffer.add("a", "c","one");   // seq 0
        buffer.add("a", "c","two");   // seq 1
        long cursor = buffer.lastSeq();
        buffer.add("a", "c","three"); // seq 2
        buffer.add("a", "c","four");  // seq 3

        List<AgentLogBuffer.LogLine> fresh = buffer.since(cursor);
        assertEquals(2, fresh.size());
        assertEquals("three", fresh.get(0).message());
        assertEquals("four", fresh.get(1).message());
        assertEquals(3, fresh.get(1).seq());
    }

    @Test
    public void emptyBufferHasNoLinesAndSeqMinusOne() {
        AgentLogBuffer buffer = new AgentLogBuffer(10);
        assertTrue(buffer.tail(5).isEmpty());
        assertTrue(buffer.since(-1).isEmpty());
        assertEquals(-1, buffer.lastSeq());
    }
}
