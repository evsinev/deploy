package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.PathGuard;
import io.pne.deploy.agent.steps.policy.StepPolicy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tells the process supervisor to signal a service, which is how a service is asked to pick up a new version.
 *
 * <p>This is the only step that starts a process. It runs one configured control program with a fixed argument
 * list - never a shell - and only for a directory the policy names, so a plan cannot turn it into a way of running
 * arbitrary commands.
 */
public class SignalServiceStep implements IStep {

    public static final String TYPE = "signal-service";

    private static final Map<String, String> SIGNAL_OPTIONS = Map.of(
              "hup",  "-h"
            , "term", "-t"
            , "up",   "-u"
            , "down", "-d"
    );

    /** Only a reload is in use today; the rest stay refused until something needs them. */
    private static final List<String> ALLOWED_SIGNALS = Arrays.asList("hup");

    private static final int  PROCESS_TIMEOUT_SECONDS = 60;
    private static final long OUTPUT_DRAIN_MILLIS     = 2_000;

    private final String service;
    private final String signal;

    public SignalServiceStep(StepParams aParams) throws StepValidationException {
        service = aParams.required("service");
        signal  = aParams.optional("signal", "hup");
        if (!SIGNAL_OPTIONS.containsKey(signal)) {
            throw aParams.error("signal", "must be one of " + SIGNAL_OPTIONS.keySet() + ", got '" + signal + "'");
        }
        if (!ALLOWED_SIGNALS.contains(signal)) {
            throw aParams.error("signal", "'" + signal + "' is not enabled; allowed signals are " + ALLOWED_SIGNALS);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "service", service);
        PathGuard.checkServiceDir(aPolicy, TYPE, "service", StepPlanScope.withPlaceholders(service, "0"));

        Path binary = aPolicy.getServiceControlBinary();
        if (binary == null || !Files.isExecutable(binary)) {
            throw new StepValidationException("step '" + TYPE + "': the service control program " + binary
                    + " is missing or not executable");
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        StepPolicy policy = aContext.getPolicy();
        try {
            Path directory = PathGuard.checkServiceDir(policy, TYPE, "service", aContext.expand(service));
            Path binary    = policy.getServiceControlBinary();

            List<String> command = Arrays.asList(binary.toString(), SIGNAL_OPTIONS.get(signal), directory.toString());
            aContext.log("running " + String.join(" ", command));

            runControlProgram(aContext, command);

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot signal " + service + ": " + e, e);
        }
    }

    /**
     * Runs the control program and waits for it, with the output read on another thread. Reading on this thread
     * would mean a program that holds its output open is never timed out, since the wait would never be reached.
     * The process is killed on a timeout and on an interrupt, so nothing is left running behind the step.
     */
    private void runControlProgram(StepContext aContext, List<String> aCommand) throws IOException, StepExecutionException {
        Process process = new ProcessBuilder(aCommand).redirectErrorStream(true).start();

        Thread reader = new Thread(() -> {
            try (BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = output.readLine()) != null) {
                    aContext.log(line);
                }
            } catch (IOException e) {
                aContext.log("cannot read the output of the service control program: " + e);
            }
        });
        reader.setName("service-control-output");
        reader.setDaemon(true);
        reader.start();

        try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new StepExecutionException("The service control program did not finish within "
                        + PROCESS_TIMEOUT_SECONDS + "s");
            }
            reader.join(OUTPUT_DRAIN_MILLIS);

            int exitValue = process.exitValue();
            if (exitValue != 0) {
                throw new StepExecutionException("The service control program returned " + exitValue);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepExecutionException("Interrupted while signalling " + service, e);
        } finally {
            stop(process);
            reader.interrupt();
        }
    }

    /**
     * Stops the control program and the children it still has.
     *
     * <p>A child that outlives its parent is reparented and can no longer be found from here, so a control
     * program that leaves something running in the background escapes this. The control program is named by the
     * policy rather than by the plan, so that is a matter of configuring a sound one, not something a plan can
     * bring about.
     */
    private static void stop(Process aProcess) {
        aProcess.descendants().forEach(ProcessHandle::destroyForcibly);
        if (aProcess.isAlive()) {
            aProcess.destroyForcibly();
        }
    }

    @Override
    public long getMaxSeconds() {
        return PROCESS_TIMEOUT_SECONDS;
    }
}
