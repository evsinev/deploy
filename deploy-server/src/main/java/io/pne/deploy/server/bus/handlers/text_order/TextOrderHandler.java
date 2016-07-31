package io.pne.deploy.server.bus.handlers.text_order;

import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.bus.handlers.order.OrderAction;
import io.pne.deploy.server.model.Command;
import io.pne.deploy.server.model.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TextOrderHandler implements IHandler<TextOrderAction> {

    @Override
    public void handle(TextOrderAction aAction, IHandlerContext aContext) {

        List<Command> commands = new ArrayList<>();
        for (CommandLine commandLine : aAction.commands) {
            commands.add(commandLine.createCommand());
        }

        Order order = new Order(
                  UUID.randomUUID().toString()
                , aAction.issue
                , commands
        );

        aContext.send(new OrderAction(order));
    }
}
