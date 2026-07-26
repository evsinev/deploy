package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.AgentInfo;
import io.pne.deploy.server.vertx.AgentRegistry.AgentRecord;
import io.pne.deploy.server.vertx.AgentRegistry.Status;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentRegistryTest {

    @Test
    public void onConnectAddsConnectedRecordWithIp() {
        AgentRegistry registry = new AgentRegistry();
        registry.onConnect("agent-1", "10.0.0.1");

        List<AgentRecord> snapshot = registry.snapshot();
        assertEquals(1, snapshot.size());
        AgentRecord record = snapshot.get(0);
        assertEquals("agent-1", record.agentId);
        assertEquals("10.0.0.1", record.ip);
        assertEquals(Status.CONNECTED, record.status);
        assertNull(record.version);
        assertNull(record.heapUsed);
    }

    @Test
    public void onInfoAttachesVersionAndHeap() {
        AgentRegistry registry = new AgentRegistry();
        registry.onConnect("agent-1", "10.0.0.1");
        registry.onInfo("agent-1", new AgentInfo("1.0-15", 111L, 222L));

        AgentRecord record = registry.snapshot().get(0);
        assertEquals("1.0-15", record.version);
        assertEquals(Long.valueOf(111L), record.heapUsed);
        assertEquals(Long.valueOf(222L), record.heapMax);
        assertEquals(Status.CONNECTED, record.status);
    }

    @Test
    public void onDisconnectKeepsRowAndMarksDisconnected() {
        AgentRegistry registry = new AgentRegistry();
        registry.onConnect("agent-1", "10.0.0.1");
        registry.onInfo("agent-1", new AgentInfo("1.0-15", 1L, 2L));
        registry.onDisconnect("agent-1");

        List<AgentRecord> snapshot = registry.snapshot();
        assertEquals(1, snapshot.size());
        AgentRecord record = snapshot.get(0);
        assertEquals(Status.DISCONNECTED, record.status);
        assertEquals("1.0-15", record.version); // pushed info is retained after disconnect
    }

    @Test
    public void reconnectFlipsBackToConnectedAndUpdatesIp() {
        AgentRegistry registry = new AgentRegistry();
        registry.onConnect("agent-1", "10.0.0.1");
        registry.onDisconnect("agent-1");
        registry.onConnect("agent-1", "10.0.0.2");

        List<AgentRecord> snapshot = registry.snapshot();
        assertEquals(1, snapshot.size());
        assertEquals(Status.CONNECTED, snapshot.get(0).status);
        assertEquals("10.0.0.2", snapshot.get(0).ip);
    }

    @Test
    public void snapshotOrdersConnectedFirstThenById() {
        AgentRegistry registry = new AgentRegistry();
        registry.onConnect("zeta", "1.1.1.1");
        registry.onConnect("alpha", "1.1.1.2");
        registry.onConnect("beta", "1.1.1.3");
        registry.onDisconnect("beta");

        List<AgentRecord> snapshot = registry.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals("alpha", snapshot.get(0).agentId); // connected, alphabetical
        assertEquals("zeta", snapshot.get(1).agentId);
        assertEquals("beta", snapshot.get(2).agentId);  // disconnected last
        assertEquals(Status.DISCONNECTED, snapshot.get(2).status);
    }

    @Test
    public void onDisconnectUnknownAgentIsNoOp() {
        AgentRegistry registry = new AgentRegistry();
        registry.onDisconnect("ghost");
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    public void onInfoBeforeConnectDefaultsToConnected() {
        AgentRegistry registry = new AgentRegistry();
        registry.onInfo("agent-1", new AgentInfo("1.0-15", 1L, 2L));

        AgentRecord record = registry.snapshot().get(0);
        assertEquals(Status.CONNECTED, record.status);
        assertEquals("1.0-15", record.version);
    }
}
