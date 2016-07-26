package io.pne.deploy.agent.tasks;

import io.pne.deploy.api.tasks.ShellScriptParameters;

public interface ITaskService {

    void runScript(ShellScriptParameters aParameters, ITaskContext aContext);

}
