package io.pne.deploy.agent.websocket;

import io.pne.deploy.api.IClientMessage;

public interface IMessageSender {

    void enqueue(IClientMessage aMessage);

}
