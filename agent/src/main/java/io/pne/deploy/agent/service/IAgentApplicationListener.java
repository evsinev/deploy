package io.pne.deploy.agent.service;

import io.pne.deploy.agent.api.messages.IAgentServerMessage;

public interface IAgentApplicationListener {
    void didConnected();

    <T extends IAgentServerMessage> void didReceiveMessage(T aMessage);
}
