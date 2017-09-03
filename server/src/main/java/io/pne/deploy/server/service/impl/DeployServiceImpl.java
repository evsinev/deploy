package io.pne.deploy.server.service.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.server.agent.IAgentFinderService;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.service.impl.alias.AliasParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class DeployServiceImpl implements IDeployService {

    private static final Logger LOG = LoggerFactory.getLogger(DeployServiceImpl.class);

    private final IAgentFinderService agentFinderService;
    private final AliasParser aliasesParser;

    public DeployServiceImpl(IAgentFinderService agentFinderService, File aAliasesDir) {
        this.agentFinderService = agentFinderService;
        aliasesParser = new AliasParser(aAliasesDir);
    }

    @Override
    public void runTask(Task aTask) throws TaskException {
        int commandNumber = 0;
        LOG.debug("Starting task: {}", aTask);
        for (TaskCommand command : aTask.commands) {
            for (String agentId : command.agents.getIds()) {
                try {
                    IAgentService agentServiceById = agentFinderService.findAgentServiceById(agentId);
                    commandNumber++;
                    String commandId = aTask.id.toString() + "-" + commandNumber;
                    agentServiceById.runCommand(new RunAgentCommandRequest(agentId, commandId, command.command));;
                } catch (AgentCommandException e) {
                    throw new TaskException("Couldn't execute command: " + command);
                }
            }
        }
    }

    @Override
    public Task parseAlias(String aText) throws TaskException {
        try {
            return aliasesParser.parseAlias(aText);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read alias file", e) ;
        }
    }
}
