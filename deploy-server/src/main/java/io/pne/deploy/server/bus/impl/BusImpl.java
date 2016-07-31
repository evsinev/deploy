package io.pne.deploy.server.bus.impl;

import io.pne.deploy.server.bus.IAction;
import io.pne.deploy.server.bus.IBus;
import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.bus.handlers.client_command.ClientCommandAction;
import io.pne.deploy.server.bus.handlers.client_command.ClientCommandHandler;
import io.pne.deploy.server.bus.handlers.order.OrderAction;
import io.pne.deploy.server.bus.handlers.order.OrderHandler;
import io.pne.deploy.server.bus.handlers.run_script.RunScriptAction;
import io.pne.deploy.server.bus.handlers.run_script.RunScriptHandler;
import io.pne.deploy.server.bus.handlers.script_log.ScriptLogAction;
import io.pne.deploy.server.bus.handlers.script_log.ScriptLogHandler;
import io.pne.deploy.server.bus.handlers.text_order.TextOrderAction;
import io.pne.deploy.server.bus.handlers.text_order.TextOrderHandler;
import io.pne.deploy.server.bus.handlers.websocket_frame.WebSocketFrameAction;
import io.pne.deploy.server.bus.handlers.websocket_frame.WebSocketFrameHandler;
import io.pne.deploy.server.httphandler.Clients;
import io.pne.deploy.server.websocket.Connections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BusImpl implements IBus {

    private static final Logger LOG = LoggerFactory.getLogger(BusImpl.class);

    private final Map<Class<? extends IAction>, IHandler> handlers;
    private final IHandlerContext context;

    public BusImpl(Clients aClients, Connections aConnections) {
        handlers = new HashMap<>();
        handlers.put(ClientCommandAction.class, new ClientCommandHandler(aClients));
        handlers.put(OrderAction.class, new OrderHandler());
        handlers.put(TextOrderAction.class, new TextOrderHandler());
        handlers.put(RunScriptAction.class, new RunScriptHandler(aConnections));
        handlers.put(WebSocketFrameAction.class, new WebSocketFrameHandler(aConnections));
        handlers.put(ScriptLogAction.class, new ScriptLogHandler(aClients));

        context = new HandlerContextImpl(this);
    }

    @Override
    public void send(IAction aAction) {
        IHandler handler = handlers.get(aAction.getClass());
        if(handler == null) {
            throw new IllegalStateException("Could not find handler for class "+ aAction.getClass().getSimpleName());
        }

        LOG.info("{} -> {}", handler.getClass().getSimpleName(), aAction);
        //noinspection unchecked
        handler.handle(aAction, context);
    }


}
