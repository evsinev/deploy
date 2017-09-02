package io.pne.deploy.agent.api;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandMessage;

public interface IAgentService {

    void runCommand(RunAgentCommandMessage aCommand) throws AgentCommandException;
}
