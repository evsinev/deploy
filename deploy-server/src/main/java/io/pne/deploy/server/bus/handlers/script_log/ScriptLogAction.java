package io.pne.deploy.server.bus.handlers.script_log;

import io.pne.deploy.api.tasks.ShellScriptLog;
import io.pne.deploy.server.bus.IAction;

public class ScriptLogAction implements IAction {

    public final ShellScriptLog log;

    public ScriptLogAction(ShellScriptLog log) {
        this.log = log;
    }

    @Override
    public String toString() {
        return "ScriptLogAction{" +
                "log=" + log +
                '}';
    }
}
