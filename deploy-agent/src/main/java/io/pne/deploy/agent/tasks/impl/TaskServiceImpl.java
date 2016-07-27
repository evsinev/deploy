package io.pne.deploy.agent.tasks.impl;

import io.pne.deploy.agent.tasks.ITaskContext;
import io.pne.deploy.agent.tasks.ITaskService;
import io.pne.deploy.agent.tasks.shellscript.ShellScriptTask;
import io.pne.deploy.api.tasks.ImmutableShellScriptResult;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskServiceImpl implements ITaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final File baseDir = new File(".");
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void runScript(ShellScriptParameters aParameters, ITaskContext aContext) {
        executor.submit((Runnable) () -> {
            try {
                ShellScriptTask task = new ShellScriptTask(baseDir);
                ShellScriptResult result = task.execute(aContext, aParameters);
                aContext.sendResultToServer(result);
            } catch (Exception e) {
                LOG.error("Could not execute " + aParameters, e);
                aContext.sendResultToServer(ImmutableShellScriptResult
                        .builder()
                        .exitCode(-1)
                        .errorMessage(e.getMessage())
                        .build());
            }
        });
    }
}
