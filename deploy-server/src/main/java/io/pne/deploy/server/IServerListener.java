package io.pne.deploy.server;

import io.pne.deploy.api.IClientMessage;

public interface IServerListener {
    void serverStopped();

    void didStarted();

    <T extends IClientMessage> void didReceiveMessage(T aMessage);

}
