package io.pne.deploy.server.vertx;

import io.vertx.core.http.ServerWebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentConnections {

    private final Map<String, ServerWebSocket> map = new ConcurrentHashMap<>();

    public AgentConnections() {
    }

    public void addAgent(ServerWebSocket aSocket) {
        String agentId = getAgentId(aSocket);
        map.put(agentId, aSocket);
    }

    public void removeAgent(ServerWebSocket aSocket) {
        map.remove(getAgentId(aSocket));
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
}
