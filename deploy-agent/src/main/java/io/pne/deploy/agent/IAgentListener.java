package io.pne.deploy.agent;

import io.pne.deploy.api.IServerMessage;

public interface IAgentListener {
    void didConnected();

    <T extends IServerMessage> void didReceiveMessage(T aMessage);
}
