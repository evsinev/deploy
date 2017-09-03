package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.server.agent.IAgentFinderService;

public class VertxAgentFinderServiceImpl implements IAgentFinderService {

    private final  AgentConnections agentConnections;
    private final Gson gson;
    private final CommandResponses commandResponses;

    public VertxAgentFinderServiceImpl(AgentConnections agentConnections, Gson gson, CommandResponses aResponses) {
        this.agentConnections = agentConnections;
        this.gson = gson;
        commandResponses = aResponses;
    }

    @Override
    public IAgentService findAgentServiceById(String aId) {
        return new VertxAgentServiceImpl(agentConnections, gson, commandResponses);
    }
}
