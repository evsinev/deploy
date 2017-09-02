package io.pne.deploy.agent.service.log;

import io.pne.deploy.agent.api.command.AgentCommandId;

public interface IAgentLogService {

    void logCommandOutput(AgentCommandId aId, String aText);
}
