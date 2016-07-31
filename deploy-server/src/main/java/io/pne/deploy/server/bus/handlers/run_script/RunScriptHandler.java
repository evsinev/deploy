package io.pne.deploy.server.bus.handlers.run_script;

import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.bus.handlers.websocket_frame.WebSocketFrameAction;
import io.pne.deploy.server.websocket.Connections;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class RunScriptHandler implements IHandler<RunScriptAction> {

    private static final Logger LOG = LoggerFactory.getLogger(RunScriptHandler.class);

    private final Connections connections;

    public RunScriptHandler(Connections connections) {
        this.connections = connections;
    }

    @Override
    public void handle(RunScriptAction aAction, IHandlerContext aContext) {
        ServerWebSocket socket = connections.getSocket(aAction.host);
        if(socket == null ) {
            LOG.warn("No host {} connected to execute script {}", aAction.host, aAction.scriptName);
            return;
        }

        ImmutableShellScriptParameters shell = ImmutableShellScriptParameters.builder()
                .filename(aAction.scriptName)
                .group("../deploy-agent/src/test/resources/scripts")
                .taskId(aAction.commandId)
                .username(ShellScriptParameters.USERNAME_NON_ROOT)
                .build();

        aContext.send(new WebSocketFrameAction(aAction.host, shell));
    }
}
