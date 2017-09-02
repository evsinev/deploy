package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandId;
import io.pne.deploy.agent.api.messages.RunAgentCommandMessage;
import io.pne.deploy.agent.service.log.IAgentLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AgentServiceImpl implements IAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final IAgentLogService logService;

    public AgentServiceImpl(IAgentLogService logService) {
        this.logService = logService;
    }

    @Override
    public void runCommand(RunAgentCommandMessage aCommand) throws AgentCommandException {

        AgentCommandId id = aCommand.command.id;
        String logId = id.getId();
        LOG.debug("{}: Running command {}", logId, aCommand);
        Process process;

        try {
            process = new ProcessBuilder(createCommandWithArguments(aCommand.command)).start();
        } catch (IOException e) {
            throw new AgentCommandException(id, "Can't start command", e);
        }

        startListenProcessOutput(process.getInputStream(), logService, id);
        startListenProcessOutput(process.getErrorStream(), logService, id);

        try {
            LOG.debug("{}: Waiting for process exit ...", logId);

            int ret = process.waitFor();
            if(ret != 0) {
                throw new AgentCommandException(id, "Command returned " + ret);
            }
            LOG.debug("{}: exit value is {}", logId, ret);
        } catch (InterruptedException e) {
            throw new AgentCommandException(id, "Can't run command", e);
        }
    }

    private static void startListenProcessOutput(InputStream aInputStream, IAgentLogService aLogService, AgentCommandId aId) {
        Thread thread = new Thread(() -> {

            LOG.debug("{}: Scanning output from process ...", aId.getId());
            Scanner scanner = new Scanner(aInputStream, "UTF-8");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                LOG.debug("{}: output is {}", aId.getId(), line);
                aLogService.logCommandOutput(aId, line);
            }
            LOG.debug("{}: Finished scanning output from the process", aId.getId());
        });
        thread.setName("cmd-" + aId.getId());
        thread.start();
    }

    private List<String> createCommandWithArguments(AgentCommand aCommand) {
        List<String> ret = new ArrayList<>();
        ret.add(aCommand.name);
        ret.addAll(aCommand.arguments);
        return ret;
    }
}
