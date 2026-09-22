package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.service.log.IAgentLogService;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepPlanExecutor;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Runs what the server asks for: either a step plan checked against the local policy, or - while the step plans are
 * still being adopted - a legacy command started as a process.
 */
public class AgentServiceImpl implements IAgentService {

    private static final Logger LOG = LoggerFactory.getLogger("Agent");


    private final IAgentLogService logService;
    private final StepRegistry     stepRegistry;
    @Nullable
    private final StepPolicy       policy;

    public AgentServiceImpl(IAgentLogService logService) {
        this(logService, null, StepRegistry.defaults());
    }

    public AgentServiceImpl(IAgentLogService aLogService, @Nullable StepPolicy aPolicy) {
        this(aLogService, aPolicy, StepRegistry.defaults());
    }

    public AgentServiceImpl(IAgentLogService aLogService, @Nullable StepPolicy aPolicy, StepRegistry aStepRegistry) {
        logService   = aLogService;
        policy       = aPolicy;
        stepRegistry = aStepRegistry;
    }

    /** True when this agent can run step plans, which the server asks before sending one. */
    public boolean isStepsSupported() {
        return policy != null;
    }

    @Override
    public void runCommand(RunAgentCommandRequest aCommand) throws AgentCommandException {

        String logId = aCommand.commandId;
        LOG.info("{}: Running command {}", logId, aCommand);

        AgentCommandType type = aCommand.command.type;
        if (type == AgentCommandType.STEPS) {
            runSteps(aCommand);
            return;
        }
        if (type == AgentCommandType.SHELL) {
            runShell(aCommand);
            return;
        }
        throw new AgentCommandException("Unsupported command type " + type
                + "; this agent understands " + AgentCommandType.SHELL + " and " + AgentCommandType.STEPS);
    }

    private void runSteps(RunAgentCommandRequest aCommand) throws AgentCommandException {
        if (policy == null) {
            throw new AgentCommandException("This agent has no policy file, so step plans are refused."
                    + " Install the policy file that states which sources, directories and services are allowed.");
        }

        String logId = aCommand.commandId;
        try {
            new StepPlanExecutor(stepRegistry, policy)
                    .run(aCommand.command.getSteps(), aLine -> logStep(logId, aLine));
        } catch (StepValidationException | StepExecutionException e) {
            throw new AgentCommandException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new AgentCommandException("The plan failed: " + e, e);
        }
    }

    private void runShell(RunAgentCommandRequest aCommand) throws AgentCommandException {
        if (policy != null && !policy.isShellAllowed()) {
            throw new AgentCommandException("Legacy commands are switched off on this agent by its policy;"
                    + " send a step plan instead");
        }
        checkShellPrefix(aCommand.command.name);

        String logId = aCommand.commandId;
        Process process;

        try {
            ensureExecutable(aCommand.command.name);
            process = new ProcessBuilder(createCommandWithArguments(aCommand.command)).start();
        } catch (IOException e) {
            throw new AgentCommandException("Can't start command", e);
        }

        startListenProcessOutput(process.getInputStream(), logService, logId);
        startListenProcessOutput(process.getErrorStream(), logService, logId);

        try {
            LOG.debug("{}: Waiting for process exit ...", logId);

            int ret = process.waitFor();
            if(ret != 0) {
                throw new AgentCommandException("Command returned " + ret);
            }
            LOG.info("{}: exit value is {}", logId, ret);
        } catch (InterruptedException e) {
            throw new AgentCommandException("Can't runner command", e);
        }
    }

    /**
     * Keeps a legacy command inside the directory the policy names. The comparison is made on real paths rather
     * than on the text of the command: a command written as if it were inside that directory can otherwise climb
     * out of it with {@code ..}, or be a link pointing at a program somewhere else entirely.
     */
    private void checkShellPrefix(String aName) throws AgentCommandException {
        if (policy == null) {
            return;
        }
        String prefix = policy.getShellAllowedPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (aName == null) {
            throw new AgentCommandException("Command has no name");
        }

        Path allowed  = realPathOf(Paths.get(prefix));
        Path resolved = realPathOf(Paths.get(aName));
        if (!resolved.startsWith(allowed) || resolved.equals(allowed)) {
            throw new AgentCommandException("Command '" + aName + "' is not inside the allowed directory '"
                    + prefix + "'");
        }
    }

    /** The real location of a path, so a link inside the allowed directory cannot point at a program outside it. */
    private static Path realPathOf(Path aPath) {
        try {
            return aPath.toRealPath();
        } catch (IOException e) {
            return aPath.toAbsolutePath().normalize();
        }
    }

    private void logStep(String aCommandId, String aLine) {
        LOG.info("{}: {}", aCommandId, aLine);
        logService.logCommandOutput(aCommandId, aLine);
    }

    private static void startListenProcessOutput(InputStream aInputStream, IAgentLogService aLogService, String aId) {
        Thread thread = new Thread(() -> {

            LOG.debug("{}: Scanning output from process ...", aId);
            Scanner scanner = new Scanner(aInputStream, "UTF-8");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                LOG.info("{}: {}", aId, line);
                aLogService.logCommandOutput(aId, line);
            }
            LOG.debug("{}: Finished scanning output from the process", aId);
        });
        thread.setName("cmd-" + aId);
        thread.start();
    }

    private List<String> createCommandWithArguments(AgentCommand aCommand) {
        List<String> ret = new ArrayList<>();
        ret.add(aCommand.name);
        ret.addAll(aCommand.arguments);
        return ret;
    }

    /**
     * Deploy often strips the execute bit off scripts. When argv[0] is a {@code .sh} file that exists but isn't
     * executable, add the owner execute bit so the direct {@code exec} can launch it (the script still needs a shebang).
     */
    private void ensureExecutable(String aName) {
        if (aName == null || !aName.endsWith(".sh")) {
            return;
        }
        File file = new File(aName);
        if (!file.isFile()) {
            LOG.debug("Script {} not found as a file, running as-is", aName);
            return;
        }
        if (file.canExecute()) {
            return;
        }
        if (file.setExecutable(true)) {
            LOG.info("Added execute bit to {}", aName);
        } else {
            LOG.warn("Could not add execute bit to {} (running anyway)", aName);
        }
    }
}
