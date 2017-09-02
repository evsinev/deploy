package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerListenerNoOp implements IServerListener {
    private static final Logger LOG = LoggerFactory.getLogger(ServerListenerNoOp.class);

    @Override
    public void serverStopped() {
        LOG.info("Server stopped");
    }

    @Override
    public void didStarted() {
        LOG.info("Server started");
    }

    @Override
    public <T extends IAgentClientMessage> void didReceiveMessage(T aMessage) {
        LOG.info("Received message: {}", aMessage);
    }

    @Override
    public void didStartFailed(Throwable aException) {
        LOG.error("Exiting with status -1", aException);
        System.exit(-1);
    }
}
