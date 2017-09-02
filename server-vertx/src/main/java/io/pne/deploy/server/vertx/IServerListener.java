package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;

public interface IServerListener {
    void serverStopped();

    void didStarted();

    <T extends IAgentClientMessage> void didReceiveMessage(T aMessage);

    void didStartFailed(Throwable cause);
}
