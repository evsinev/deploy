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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tells the supervisor to act on a service, which is how a service is asked to pick up a new version.
 *
 * <p>By default the command is written straight to the supervisor's control channel, so nothing is executed:
 * the agent starts no process at all, and a host watching for unexpected launches inside the container sees
 * none. Where the supervisor does not work that way, the policy can name a control program to run instead.
 */
public class SignalServiceStep implements IStep {

    public static final String TYPE = "signal-service";

    /** What the supervisor understands: one letter per command, and the option of the program that sends it. */
    private static final Map<String, Command> COMMANDS = commands();

    private static final int  PROCESS_TIMEOUT_SECONDS = 60;
    private static final long OUTPUT_DRAIN_MILLIS     = 2_000;

    private final String service;
    private final String signal;

    public SignalServiceStep(StepParams aParams) throws StepValidationException {
        service = aParams.required("service");
        signal  = aParams.optional("signal", "hup");
        if (!COMMANDS.containsKey(signal)) {
            throw aParams.error("signal", "must be one of " + COMMANDS.keySet() + ", got '" + signal + "'");
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "service", service);
        Path directory = PathGuard.checkServiceDir(aPolicy, TYPE, "service", StepPlanScope.withPlaceholders(service, "0"));

        if (aPolicy.isServiceControlledByProgram()) {
            Path binary = aPolicy.getServiceControlBinary();
            if (binary == null || !Files.isExecutable(binary)) {
                throw new StepValidationException("step '" + TYPE + "': the service control program " + binary
                        + " is missing or not executable");
            }
            return;
        }

        // Nothing to run, so the one thing worth knowing up front is whether this really is a supervised service.
        if (Files.isDirectory(directory) && !SuperviseControl.looksSupervised(directory)) {
            throw new StepValidationException("step '" + TYPE + "': " + SuperviseControl.controlFile(directory)
                    + " does not exist, so " + directory + " is not a service a supervisor is managing");
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        StepPolicy policy = aContext.getPolicy();
        try {
            Path    directory = PathGuard.checkServiceDir(policy, TYPE, "service", aContext.expand(service));
            Command command   = COMMANDS.get(signal);

            if (policy.isServiceControlledByProgram()) {
                runControlProgram(aContext, Arrays.asList(
                        policy.getServiceControlBinary().toString(), command.option, directory.toString()));
                return;
            }

            Path control = SuperviseControl.controlFile(directory);
            aContext.log("sending '" + command.letter + "' (" + signal + ") to " + control);
            SuperviseControl.send(control, command.letter);

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot signal " + service + ": " + e.getMessage(), e);
        }
    }

    /**
     * Runs the control program and waits for it, with the output read on another thread. Reading on this thread
     * would mean a program that holds its output open is never timed out, since the wait would never be reached.
     * The process is killed on a timeout and on an interrupt, so nothing is left running behind the step.
     */
    private void runControlProgram(StepContext aContext, List<String> aCommand) throws IOException, StepExecutionException {
        aContext.log("running " + String.join(" ", aCommand));
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

    private static Map<String, Command> commands() {
        Map<String, Command> commands = new LinkedHashMap<>();
        commands.put("hup",  new Command('h', "-h"));
        commands.put("term", new Command('t', "-t"));
        return commands;
    }

    /** One supervisor command: the letter written to the control channel, and the option of the control program. */
    private static final class Command {

        private final char   letter;
        private final String option;

        private Command(char aLetter, String aOption) {
            letter = aLetter;
            option = aOption;
        }
    }
}
