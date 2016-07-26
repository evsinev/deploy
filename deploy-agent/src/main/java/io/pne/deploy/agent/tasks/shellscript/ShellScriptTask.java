package io.pne.deploy.agent.tasks.shellscript;

import io.pne.deploy.agent.tasks.ITaskContext;
import io.pne.deploy.api.tasks.ImmutableShellScriptResult;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ShellScriptTask {

    private static final Logger LOG = LoggerFactory.getLogger(ShellScriptTask.class);

    private final File baseDir;

    public ShellScriptTask(File aBaseDir) {
        baseDir = aBaseDir;
    }

    public ShellScriptResult execute(ITaskContext aContext, ShellScriptParameters aParameters) {

        File workingDir = aParameters.group().isPresent() ? new File(baseDir, aParameters.group().get()) : baseDir;
        File scriptFile = new File(workingDir, aParameters.filename());
        validate(scriptFile);

        ProcessBuilder processBuilder = createProcessBuilder(aParameters, workingDir, scriptFile);

        try {
            Process process = processBuilder.start();

            String                  threadName = createThreadName(aParameters.filename(), process);
            ShellScriptLoggerThread logger     = new ShellScriptLoggerThread(threadName, aContext, process.getInputStream());
            logger.start();

            int result = process.waitFor();
            LOG.debug("{}: exit code is {}", threadName, result);

            return ImmutableShellScriptResult.builder()
                    .exitCode(result)
                    .build();

        } catch (IOException e) {
            throw new IllegalStateException("IO error while executing script", e);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Process interrupted", e);
        }

    }

    private ProcessBuilder createProcessBuilder(ShellScriptParameters aParameters, File workingDir, File scriptFile) {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(createCommandArguments(scriptFile, aParameters.username(), aParameters.arguments()));
        builder.directory(workingDir);
        builder.redirectErrorStream(true);
        if(aParameters.environment().isPresent()) {
            builder.environment().putAll(aParameters.environment().get());
        }
        return builder;
    }

    private String createThreadName(String aFilename, Process aProcess) {
        long pid;
        try {
            Class clazz = aProcess.getClass();
            Field pidField = clazz.getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(aProcess);
            pidField.setAccessible(false);
        } catch (Exception e) {
            LOG.error("Could not get pid", e);
            pid = System.currentTimeMillis();
        }

        return aFilename + "-" + pid;
    }

    private static String[] createCommandArguments(File aScriptFile, String aUsername, Optional<String[]> aArguments) {
        List<String> args = new ArrayList<>();
        if(!aUsername.equals("non-root")) {
            args.add("su");
            args.add("-l");
            args.add(aUsername);
        }
        args.add("sh");
        args.add(aScriptFile.getAbsolutePath());
        if(aArguments.isPresent()) {
            args.addAll(Arrays.asList(aArguments.get()));
        }
        return args.toArray(new String[args.size()]);
    }

    private void validate(File scriptFile) {
        if(!scriptFile.exists()) {
            throw new IllegalStateException("File " + scriptFile + " not found");
        }
    }
}
