package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.server.api.task.*;
import io.pne.deploy.server.service.impl.InputTextChecker;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.pne.deploy.server.api.task.AgentFinder.agentByName;

public class AliasParser {

    private final AliasDescriptionLoader loader;
    private final InputTextChecker       checker = new InputTextChecker();

    public AliasParser(File aAliasesDir) {
        if(!aAliasesDir.exists()) {
            throw new IllegalStateException("Aliases dir " + aAliasesDir + " not exists");
        }
        loader = new AliasDescriptionLoader(aAliasesDir);
    }

    public Task parseAlias(String aText, int aIssueId) throws IOException {
        checker.checkAlias(aText);
        AliasParameters aliasParameters = new AliasParameters(aText);
        AliasDescription description    = loader.loadAlias(aliasParameters);
        return convertAliasDescriptionToTask(description, aText, aIssueId);
    }

    private Task convertAliasDescriptionToTask(AliasDescription description, String aText, int aIssueId) {
        List<TaskCommand> commands = new ArrayList<>();
        if(description.commands == null) {
            throw new IllegalArgumentException("description.commands is null");
        }
        for (AliasCommand command : description.commands) {
            AgentCommand agentCommand = new AgentCommand(
                    new AgentCommandParameters()
                    , AgentCommandType.SHELL
                    , command.name
                    , command.arguments

            );
            commands.add(new TaskCommand(agentByName(command.agents), agentCommand));
        }
        return new Task(TaskId.generateTaskId(), new TaskParameters(), commands, aText, aIssueId);
    }
}
