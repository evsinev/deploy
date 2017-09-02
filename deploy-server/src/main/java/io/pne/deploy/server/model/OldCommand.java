package io.pne.deploy.server.model;

import java.util.Map;

public class OldCommand {

    public final String              commandId;
    public final String              commandName;
    public final Map<String, String> parameters;
    public final CommandState        commandState;

    public OldCommand(String commandId, String commandName, Map<String, String> parameters, CommandState commandState) {
        this.commandId = commandId;
        this.commandName = commandName;
        this.parameters = parameters;
        this.commandState = commandState;
    }

    @Override
    public String toString() {
        return "OldCommand{" +
                "commandId='" + commandId + '\'' +
                ", commandName='" + commandName + '\'' +
                ", parameters=" + parameters +
                ", commandState=" + commandState +
                '}';
    }
}
