package io.pne.deploy.server.bus.handlers.client_command;

import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.bus.handlers.text_order.CommandLine;
import io.pne.deploy.server.bus.handlers.text_order.TextOrderAction;
import io.pne.deploy.server.httphandler.ClientConnections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

public class ClientCommandHandler implements IHandler<ClientCommandAction> {

    private final ClientConnections clients;

    private static final Logger LOG = LoggerFactory.getLogger(ClientCommandHandler.class);

    public ClientCommandHandler(ClientConnections clients) {
        this.clients = clients;
    }

    @Override
    public void handle(ClientCommandAction aAction, IHandlerContext aContext) {
        String commandId = UUID.randomUUID().toString();

        clients.addClient(commandId, aAction.httpResponse);

        TextOrderAction orderAction = new TextOrderAction(
                  aAction.issueId
                , Arrays.asList(new CommandLine(commandId, aAction.command))
        );

        aContext.send(orderAction);
    }
}
