package io.pne.deploy.server.vertx.dashboard;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentLogBufferTest {

    @Test
    public void keepsMostRecentUpToCapacityNewestFirst() {
        AgentLogBuffer buffer = new AgentLogBuffer(3);
        buffer.add("c", "one");
        buffer.add("c", "two");
        buffer.add("c", "three");
        buffer.add("c", "four"); // evicts "one"

        List<AgentLogBuffer.LogLine> snap = buffer.snapshot(10);
        assertEquals(3, snap.size());
        assertEquals("four", snap.get(0).message());   // newest first
        assertEquals("three", snap.get(1).message());
        assertEquals("two", snap.get(2).message());
    }

    @Test
    public void snapshotRespectsMax() {
        AgentLogBuffer buffer = new AgentLogBuffer(100);
        for (int i = 0; i < 10; i++) {
            buffer.add("c", "m" + i);
        }
        List<AgentLogBuffer.LogLine> snap = buffer.snapshot(3);
        assertEquals(3, snap.size());
        assertEquals("m9", snap.get(0).message());
    }

    @Test
    public void emptyBufferSnapshotIsEmpty() {
        assertTrue(new AgentLogBuffer(10).snapshot(5).isEmpty());
    }
}
