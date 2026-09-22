package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.service.log.IAgentLogService;
import io.pne.deploy.server.agent.IAgentFinderService;
import io.pne.deploy.server.agent.impl.LocalAgentServiceImpl;
import io.pne.deploy.server.api.ITaskExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxAgentFinderServiceImpl implements IAgentFinderService {

    private static final Logger LOG = LoggerFactory.getLogger(VertxAgentFinderServiceImpl.class);

    private final  AgentConnections agentConnections;
    private final Gson gson;
    private final CommandResponses commandResponses;
    private final LocalAgentServiceImpl localAgentService = new LocalAgentServiceImpl((aCommandId, aText) -> LOG.info("{}: {}", aCommandId, aText));
    private final ITaskExecutionListener taskListener;
    private final AgentRegistry          registry;

    public VertxAgentFinderServiceImpl(AgentConnections agentConnections, Gson gson, CommandResponses aResponses, ITaskExecutionListener aListener) {
        this(agentConnections, gson, aResponses, aListener, null);
    }

    public VertxAgentFinderServiceImpl(AgentConnections agentConnections, Gson gson, CommandResponses aResponses, ITaskExecutionListener aListener, AgentRegistry aRegistry) {
        this.agentConnections = agentConnections;
        this.gson = gson;
        commandResponses = aResponses;
        taskListener = aListener;
        registry = aRegistry;
    }

    @Override
    public IAgentService findAgentServiceById(String aId) {
        if(aId.equals("localhost")) {
           return localAgentService;
        }

        return new VertxAgentServiceImpl(agentConnections, gson, commandResponses, taskListener, registry);
    }
}
