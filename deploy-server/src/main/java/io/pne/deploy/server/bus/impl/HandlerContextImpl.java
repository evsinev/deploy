package io.pne.deploy.server.bus.impl;

import io.pne.deploy.server.bus.IAction;
import io.pne.deploy.server.bus.IBus;
import io.pne.deploy.server.bus.IHandlerContext;

public class HandlerContextImpl implements IHandlerContext {

    private final IBus bus;

    public HandlerContextImpl(IBus bus) {
        this.bus = bus;
    }

    @Override
    public void send(IAction aAction) {
        bus.send(aAction);
    }
}
