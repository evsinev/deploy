package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.server.agent.IAgentFinderService;

public class VertxAgentFinderServiceImpl implements IAgentFinderService {

    private final  AgentConnections agentConnections;
    private final Gson gson;

    public VertxAgentFinderServiceImpl(AgentConnections agentConnections, Gson gson) {
        this.agentConnections = agentConnections;
        this.gson = gson;
    }

    @Override
    public IAgentService findAgentServiceById(String aId) {
        return new VertxAgentServiceImpl(agentConnections, gson);
    }
}
