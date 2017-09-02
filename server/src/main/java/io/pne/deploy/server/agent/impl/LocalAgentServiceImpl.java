package io.pne.deploy.server.agent.impl;

import io.pne.deploy.agent.service.impl.AgentServiceImpl;
import io.pne.deploy.agent.service.log.IAgentLogService;

public class LocalAgentServiceImpl extends AgentServiceImpl {

    public LocalAgentServiceImpl(IAgentLogService logService) {
        super(logService);
    }
}
