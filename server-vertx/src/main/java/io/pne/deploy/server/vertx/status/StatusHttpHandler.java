package io.pne.deploy.server.vertx.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.vertx.AgentConnections;
import io.pne.deploy.server.vertx.status.model.DeployStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;

import java.util.Collection;

import static org.slf4j.LoggerFactory.getLogger;

public class StatusHttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = getLogger(StatusHttpHandler.class);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final AgentConnections agentConnections;
    private final Collection<Long> issueQueue;

    private final TaskExecutionListenerImpl listener;

    public StatusHttpHandler(AgentConnections aAgentConnections, Collection<Long> aIssueQueue, ITaskExecutionListener aListener) {
        agentConnections = aAgentConnections;
        issueQueue = aIssueQueue;
        listener = (TaskExecutionListenerImpl) aListener;
    }

    @Override
    public void handle(HttpServerRequest aEvent) {
        DeployStatus status = DeployStatus.builder()
                .connectedAgents ( agentConnections.getAgentList() )
                .issueQueue      ( issueQueue                      )
                .taskStatus      ( listener.getTaskStatus()        )
                .build();

        aEvent.response()
                .putHeader("Content-Type", "application/json; charset=utf-8")
                .end(gson.toJson(status));
    }
}
