package io.pne.deploy.server.httphandler;

import io.vertx.core.http.HttpServerResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientConnections {

    private final Map<String, HttpServerResponse> map;

    public ClientConnections() {
        this.map = new ConcurrentHashMap<>();
    }

    public void addClient(String aCommand, HttpServerResponse httpResponse) {
        map.put(aCommand, httpResponse);
    }

    public HttpServerResponse get(String aCommandId) {
        return map.get(aCommandId);
    }
}
