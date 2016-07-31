package io.pne.deploy.server.model;

import java.util.Map;

public class Command {

    public final String              commandId;
    public final String              commandName;
    public final Map<String, String> parameters;

    public Command(String commandId, String commandName, Map<String, String> parameters) {
        this.commandId = commandId;
        this.commandName = commandName;
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "Command{" +
                "commandId='" + commandId + '\'' +
                ", commandName='" + commandName + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
