package io.pne.deploy.server.agent;

import io.pne.deploy.agent.api.IAgentService;

public interface IAgentFinderService {

    IAgentService findAgentServiceById(String aId);
}
