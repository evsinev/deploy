package io.pne.deploy.server.bus.handlers.order;

import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.bus.handlers.run_script.RunScriptAction;
import io.pne.deploy.server.model.OldCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class OrderHandler implements IHandler<OrderAction> {

    private static final Logger LOG = LoggerFactory.getLogger(OrderHandler.class);

    @Override
    public void handle(OrderAction aAction, IHandlerContext aContext) {
        // save order

        if(aAction.order.commands == null) {
            throw new IllegalStateException("Commands is null");
        }

        for (OldCommand command : aAction.order.commands) {
            if(command == null) {
                throw new IllegalStateException("OldCommand is null");
            }
            if(command.commandName.equals("@script")) {
                RunScriptAction scriptAction = new RunScriptAction(
                        command.commandId
                        , getScriptName(command)
                        , getHostname(command)
                        , getEnvironment(command)
                );
                aContext.send(scriptAction);
            } else {
                throw new IllegalStateException("Unknown command " + command.commandName);
            }
        }

    }

    private Map<String, String> getEnvironment(OldCommand aCommand) {
        return aCommand.parameters;
    }

    private String getHostname(OldCommand aCommand) {
        return aCommand.parameters.get("host");
    }

    private String getScriptName(OldCommand aCommand) {
        return aCommand.parameters.get("name");
    }



}
