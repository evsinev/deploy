package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.service.log.IAgentLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AgentServiceImpl implements IAgentService {

    private static final Logger LOG = LoggerFactory.getLogger("Agent");


    private final IAgentLogService logService;

    public AgentServiceImpl(IAgentLogService logService) {
        this.logService = logService;
    }

    @Override
    public void runCommand(RunAgentCommandRequest aCommand) throws AgentCommandException {

        String logId = aCommand.commandId;
        LOG.info("{}: Running command {}", logId, aCommand);
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
