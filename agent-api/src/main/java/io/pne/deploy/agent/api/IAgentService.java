package io.pne.deploy.agent.api;

import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;

public interface IAgentService {

    void runCommand(RunAgentCommandRequest aCommand) throws AgentCommandException;
}
