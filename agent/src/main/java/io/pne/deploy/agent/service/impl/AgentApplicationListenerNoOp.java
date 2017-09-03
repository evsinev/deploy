package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.service.IAgentApplicationListener;

public class AgentApplicationListenerNoOp implements IAgentApplicationListener {

    @Override
    public void didConnected() {
    }

    @Override
    public <T extends IAgentServerMessage> void didReceiveMessage(T aMessage) {
    }
}
