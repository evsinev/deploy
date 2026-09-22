package io.pne.deploy.server.service.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.server.agent.IAgentFinderService;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.ITaskExecutionListener;
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
    private final ITaskExecutionListener taskExecutionListener;

    public DeployServiceImpl(IAgentFinderService agentFinderService, File aAliasesDir, ITaskExecutionListener aTaskListener) {
        this(agentFinderService, aAliasesDir, new File(aAliasesDir.getParentFile(), "recipes"), aTaskListener);
    }

    public DeployServiceImpl(IAgentFinderService agentFinderService, File aAliasesDir, File aRecipesDir, ITaskExecutionListener aTaskListener) {
        this.agentFinderService = agentFinderService;
        aliasesParser = new AliasParser(aAliasesDir, aRecipesDir);
        taskExecutionListener = aTaskListener;
    }

    @Override
    public void runTask(Task aTask) throws TaskException {
        taskExecutionListener.onTaskStart(aTask);

        try {
            int commandNumber = 0;
            LOG.debug("Starting task: {}", aTask);
            for (TaskCommand command : aTask.commands) {
                taskExecutionListener.onCommandStart(aTask, command);
                try {
                    for (String agentId : command.agents.getIds()) {

                        IAgentService agentServiceById = agentFinderService.findAgentServiceById(agentId);
                        commandNumber++;
                        String commandId = aTask.id + "-" + commandNumber;

                        RunAgentCommandRequest agentCommand = new RunAgentCommandRequest(agentId, commandId, command.command);
                        taskExecutionListener.onAgentCommandStart(aTask, command, agentCommand);
                        try {
                            agentServiceById.runCommand(agentCommand);
                            taskExecutionListener.onAgentCommandSuccess(aTask, command, agentCommand);
                        } catch (Exception e) {
                            taskExecutionListener.onAgentCommandError(aTask, command, agentCommand, e);
                            // Carry the reason into the message: this is what reaches the issue and the chat,
                            // where a stack trace alone leaves the reader guessing what to change.
                            throw new TaskException("Agent " + agentId + " could not run the command: "
                                    + e.getMessage(), e);
                        }
                    }
                    taskExecutionListener.onCommandSuccess(aTask, command);
                } catch (Exception e) {
                    taskExecutionListener.onCommandError(aTask, command, e);
                    throw e;
                }
            }
            taskExecutionListener.onTaskSuccess(aTask);
        } catch (Exception e) {
            taskExecutionListener.onTaskError(aTask, e);
            throw e;
        }
    }

    @Override
    public Task parseAlias(String aText, int aIssueId) throws TaskException {
        try {
            return aliasesParser.parseAlias(aText, aIssueId);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read alias file", e) ;
        }
    }
}
