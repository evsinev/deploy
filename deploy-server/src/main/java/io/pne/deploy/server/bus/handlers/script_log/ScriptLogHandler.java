package io.pne.deploy.server.bus.handlers.script_log;

import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.httphandler.ClientConnections;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptLogHandler implements IHandler<ScriptLogAction> {

    private static final Logger LOG = LoggerFactory.getLogger(ScriptLogHandler.class);

    private final ClientConnections clients;

    public ScriptLogHandler(ClientConnections aClients) {
        clients = aClients;
    }

    @Override
    public void handle(ScriptLogAction aAction, IHandlerContext aContext) {
        HttpServerResponse serverResponse = clients.get(aAction.log.taskId());
        if(serverResponse == null) {
            LOG.warn("No server response for id {}", aAction.log.taskId());
            return;
        }

        serverResponse.write(aAction.log.message());
        serverResponse.write("\n");

    }
}
