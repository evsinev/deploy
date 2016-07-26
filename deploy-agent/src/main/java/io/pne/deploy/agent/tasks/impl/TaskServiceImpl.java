package io.pne.deploy.agent.tasks.impl;

import io.pne.deploy.agent.tasks.ITaskContext;
import io.pne.deploy.agent.tasks.ITaskService;
import io.pne.deploy.agent.tasks.shellscript.ShellScriptTask;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskServiceImpl implements ITaskService {

    private final File baseDir = new File(".");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void runScript(ShellScriptParameters aParameters, ITaskContext aContext) {
        executor.submit((Runnable) () -> {
            ShellScriptTask task = new ShellScriptTask(baseDir);
            ShellScriptResult result = task.execute(aContext, aParameters);
            aContext.sendResultToServer(result);
        });
    }
}
