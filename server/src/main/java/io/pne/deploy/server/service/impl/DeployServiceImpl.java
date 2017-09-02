package io.pne.deploy.server.service.impl;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandMessage;
import io.pne.deploy.server.agent.IAgentFinderService;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.Task;

public class DeployServiceImpl implements IDeployService {

    private final IAgentFinderService agentFinderService;

    public DeployServiceImpl(IAgentFinderService agentFinderService) {
        this.agentFinderService = agentFinderService;
    }

    @Override
    public void runTask(Task aTask) throws TaskException {
        for (TaskCommand command : aTask.commands) {
            for (String agentId : command.agents.getIds()) {
                try {
                    agentFinderService.findAgentServiceById(agentId).runCommand(new RunAgentCommandMessage(agentId, command.command));;
                } catch (AgentCommandException e) {
                    throw new TaskException("Couldn't execute command: " + command);
                }
            }
            //agentService.
        }
    }
}
