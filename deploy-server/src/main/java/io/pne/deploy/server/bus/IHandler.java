package io.pne.deploy.server.bus;

public interface IHandler<T extends IAction> {

    void handle(T aAction, IHandlerContext aContext);

}
