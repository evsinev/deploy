package io.pne.deploy.agent.websocket;

public interface IClientHandler<T> {

    void handle(T aMessage, IClientHandlerContext aContext);
}
