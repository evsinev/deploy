package io.pne.deploy.server.bus.handlers.websocket_frame;

import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.server.bus.IAction;

public class WebSocketFrameAction implements IAction{

    final IServerMessage message;
    final String         hostname;

    public WebSocketFrameAction(String hostname, IServerMessage message) {
        this.hostname = hostname;
        this.message = message;
    }

    @Override
    public String toString() {
        return "WebSocketFrameAction{" +
                "message=" + message +
                ", hostname='" + hostname + '\'' +
                '}';
    }
}
