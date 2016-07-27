package io.pne.deploy.agent;

import io.pne.deploy.api.IServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentListenerNoOp implements IAgentListener {

    private static final Logger LOG = LoggerFactory.getLogger(AgentListenerNoOp.class);

    @Override
    public void didConnected() {
        LOG.info("Agent did connected");
    }

    @Override
    public <T extends IServerMessage> void didReceiveMessage(T aMessage) {
        LOG.info("Received {}", aMessage);
    }
}
