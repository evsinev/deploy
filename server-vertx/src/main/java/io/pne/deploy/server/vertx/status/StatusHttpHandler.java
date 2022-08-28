package io.pne.deploy.server.vertx.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.vertx.AgentConnections;
import io.pne.deploy.server.vertx.status.model.DeployStatus;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.slf4j.LoggerFactory.getLogger;

public class StatusHttpHandler implements Handler<HttpServerRequest>, Consumer<TaskStatus> {

    private static final Logger LOG = getLogger(StatusHttpHandler.class);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final AgentConnections agentConnections;
    private final Collection<Long> issueQueue;

    private final AtomicReference<TaskStatus> deployStatusRef = new AtomicReference<>();

    public StatusHttpHandler(AgentConnections aAgentConnections, Collection<Long> aIssueQueue) {
        agentConnections = aAgentConnections;
        issueQueue = aIssueQueue;
    }

    @Override
    public void handle(HttpServerRequest aEvent) {
        DeployStatus status = DeployStatus.builder()
                .connectedAgents ( agentConnections.getAgentList() )
                .issueQueue      ( issueQueue                      )
                .taskStatus      ( deployStatusRef.get()           )
                .build();

        aEvent.response()
                .putHeader("Content-Type", "application/json; charset=utf-8")
                .end(gson.toJson(status));
    }

    @Override
    public void accept(TaskStatus aTaskStatus) {
         deployStatusRef.set(aTaskStatus);
    }
}
