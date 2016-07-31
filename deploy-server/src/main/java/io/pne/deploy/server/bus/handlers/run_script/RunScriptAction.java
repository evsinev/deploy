package io.pne.deploy.server.bus.handlers.run_script;

import io.pne.deploy.server.bus.IAction;

import java.util.Map;

public class RunScriptAction implements IAction {

    public final String             commandId;
    public final String             scriptName;
    public final String             host;
    public final Map<String,String> environment;

    public RunScriptAction(String commandId, String scriptName, String host, Map<String, String> environment) {
        this.commandId = commandId;
        this.scriptName = scriptName;
        this.host = host;
        this.environment = environment;
    }

    @Override
    public String toString() {
        return "RunScriptAction{" +
                "commandId='" + commandId + '\'' +
                ", scriptName='" + scriptName + '\'' +
                ", host='" + host + '\'' +
                ", environment=" + environment +
                '}';
    }
}
