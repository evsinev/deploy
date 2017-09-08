package io.pne.deploy.server.vertx;

import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentConnections {

    private static final Logger LOG = LoggerFactory.getLogger(AgentConnections.class);

    private final Map<String, ServerWebSocket> map = new ConcurrentHashMap<>();

    public AgentConnections() {
    }

    public void addAgent(ServerWebSocket aSocket) {
        String agentId = getAgentId(aSocket);
        LOG.info("Agent connected: {}", agentId);
        map.put(agentId, aSocket);
    }

    public void removeAgent(ServerWebSocket aSocket) {
        String agentId = getAgentId(aSocket);
        map.remove(agentId);
        LOG.info("Agent disconnected: {}", agentId);
    }

    private String getAgentId(ServerWebSocket aSocket) {
        PathParameters pathParameters = new PathParameters(aSocket.path());
        return pathParameters.getLast();
    }

    public ServerWebSocket getSocket(String aHostname) {
        ServerWebSocket socket = map.get(aHostname);
        if(socket == null) {
            throw new IllegalStateException("No server socket for host " + aHostname + " in map " + map.keySet());
        }
        return socket;
    }

    public String getAgents() {
        return map.keySet().toString();
    }
}
