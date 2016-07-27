package io.pne.deploy.server;

import io.pne.deploy.api.IClientMessage;
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
    public <T extends IClientMessage> void didReceiveMessage(T aMessage) {
        LOG.info("Received message: {}", aMessage);
    }
}
