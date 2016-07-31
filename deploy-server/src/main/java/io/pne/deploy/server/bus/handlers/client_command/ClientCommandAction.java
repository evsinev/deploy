package io.pne.deploy.server.bus.handlers.client_command;

import io.pne.deploy.server.bus.IAction;
import io.vertx.core.http.HttpServerResponse;

public class ClientCommandAction implements IAction {

    public final String             issueId;
    public final String             command;
    public final HttpServerResponse httpResponse;

    public ClientCommandAction(String issueId, String command, HttpServerResponse httpResponse) {
        this.issueId = issueId;
        this.command = command;
        this.httpResponse = httpResponse;
    }

    @Override
    public String toString() {
        return "ClientCommandAction{" +
                "issueId='" + issueId + '\'' +
                ", command='" + command + '\'' +
                ", httpResponse=" + httpResponse +
                '}';
    }
}
