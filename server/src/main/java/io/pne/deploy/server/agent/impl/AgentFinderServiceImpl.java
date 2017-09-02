package io.pne.deploy.server.agent.impl;

import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.server.agent.IAgentFinderService;

public class AgentFinderServiceImpl implements IAgentFinderService {

    private final LocalAgentServiceImpl localAgentService;

    public AgentFinderServiceImpl(LocalAgentServiceImpl localAgentService) {
        this.localAgentService = localAgentService;
    }

    @Override
    public IAgentService findAgentServiceById(String aId) {
        if("localhost".equals(aId)) {
            return localAgentService;
        }
        throw new IllegalStateException("Coundn't find agent with id " + aId);
    }
}
