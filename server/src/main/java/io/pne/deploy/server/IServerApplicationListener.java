package io.pne.deploy.server;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;

public interface IServerApplicationListener {
    void serverStopped();

    void didStarted();

    <T extends IAgentClientMessage> void didReceiveMessage(T aMessage);

    void didStartFailed(Throwable cause);
}
