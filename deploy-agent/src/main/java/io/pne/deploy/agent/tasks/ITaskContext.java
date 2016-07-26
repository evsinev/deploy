package io.pne.deploy.agent.tasks;

import io.pne.deploy.api.tasks.ShellScriptResult;

public interface ITaskContext {
    void log(String aLine);

    void sendResultToServer(ShellScriptResult aResult);
}
