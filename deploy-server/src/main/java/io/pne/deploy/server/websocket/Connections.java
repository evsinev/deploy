package io.pne.deploy.server.websocket;

import io.vertx.core.http.ServerWebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Connections {

    private final Map<String, ServerWebSocket> map = new ConcurrentHashMap<>();

    public Connections() {
    }

    public void add(ServerWebSocket aSocket) {
        String hostname = getHostname(aSocket);
        map.put(hostname, aSocket);
    }

    public void remove(ServerWebSocket aSocket) {
        map.remove(getHostname(aSocket));
    }

    private String getHostname(ServerWebSocket aSocket) {
        return aSocket.remoteAddress().host();
    }

    public ServerWebSocket getSocket(String aHostname) {
        ServerWebSocket socket = map.get(aHostname);
        if(socket == null) {
            throw new IllegalStateException("No server socket for host " + aHostname + " in map " + map.keySet());
        }
        return socket;
    }
}
