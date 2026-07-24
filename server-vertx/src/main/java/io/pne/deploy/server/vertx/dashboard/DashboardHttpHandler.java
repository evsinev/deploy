package io.pne.deploy.server.vertx.dashboard;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.vertx.AgentConnections;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Serves the live dashboard at {@code /deploy/dashboard}: a static HTML shell plus the vendored
 * htmx assets, and an SSE stream at {@code /deploy/dashboard/events} that pushes rendered HTML
 * fragments (one named event per card) on a fixed interval. No authentication.
 */
public class DashboardHttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardHttpHandler.class);

    private static final String PATH        = "/deploy/dashboard";
    private static final String PATH_EVENTS = "/deploy/dashboard/events";
    private static final String PATH_HTMX   = "/deploy/dashboard/htmx.min.js";
    private static final String PATH_SSE_JS = "/deploy/dashboard/sse.js";

    private final Vertx                       vertx;
    private final AgentConnections            agents;
    private final Collection<Long>            pendingIssues;
    private final Map<String, PersistentSpool> queues;
    private final Supplier<TaskStatus>        taskStatusSupplier;
    private final long                        refreshMs;

    private final Buffer indexHtml = readResource("/dashboard/index.html");
    private final Buffer htmxJs    = readResource("/dashboard/htmx.min.js");
    private final Buffer sseJs     = readResource("/dashboard/sse.js");

    public DashboardHttpHandler(
            Vertx aVertx
            , AgentConnections aAgents
            , Collection<Long> aPendingIssues
            , Map<String, PersistentSpool> aQueues
            , Supplier<TaskStatus> aTaskStatusSupplier
            , long aRefreshMs
    ) {
        this.vertx              = aVertx;
        this.agents             = aAgents;
        this.pendingIssues      = aPendingIssues;
        this.queues             = aQueues;
        this.taskStatusSupplier = aTaskStatusSupplier;
        this.refreshMs          = aRefreshMs;
    }

    @Override
    public void handle(HttpServerRequest aRequest) {
        String path = aRequest.path();
        if (PATH_EVENTS.equals(path)) {
            handleEvents(aRequest);
        } else if (PATH_HTMX.equals(path)) {
            serve(aRequest, "application/javascript; charset=utf-8", htmxJs);
        } else if (PATH_SSE_JS.equals(path)) {
            serve(aRequest, "application/javascript; charset=utf-8", sseJs);
        } else if (PATH.equals(path) || (PATH + "/").equals(path)) {
            serve(aRequest, "text/html; charset=utf-8", indexHtml);
        } else {
            aRequest.response().setStatusCode(404).end("Not found\n");
        }
    }

    private static void serve(HttpServerRequest aRequest, String aContentType, Buffer aBody) {
        aRequest.response().putHeader("Content-Type", aContentType).end(aBody);
    }

    private void handleEvents(HttpServerRequest aRequest) {
        HttpServerResponse response = aRequest.response();
        response.setChunked(true);
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");

        if (!pushSnapshot(response)) {
            return;
        }

        long timerId = vertx.setPeriodic(refreshMs, id -> {
            if (response.closed() || response.ended() || !pushSnapshot(response)) {
                vertx.cancelTimer(id);
            }
        });

        aRequest.connection().closeHandler(aVoid -> vertx.cancelTimer(timerId));
    }

    /** Writes one snapshot of every card; returns false if the client connection is gone. */
    private boolean pushSnapshot(HttpServerResponse aResponse) {
        try {
            Set<String>       agentSnapshot = new LinkedHashSet<>(agents.getAgentList());
            Collection<Long>  issueSnapshot = new ArrayList<>(pendingIssues);
            Runtime           runtime       = Runtime.getRuntime();
            long              heapUsed      = runtime.totalMemory() - runtime.freeMemory();

            writeEvent(aResponse, "service", DashboardView.service(
                    ManagementFactory.getRuntimeMXBean().getUptime(), heapUsed, runtime.maxMemory(), agentSnapshot.size()));
            writeEvent(aResponse, "agents", DashboardView.agents(agentSnapshot));
            writeEvent(aResponse, "status", DashboardView.status(taskStatusSupplier.get()));
            writeEvent(aResponse, "issues", DashboardView.issues(issueSnapshot));
            writeEvent(aResponse, "queues", DashboardView.queues(queues));
            return true;
        } catch (RuntimeException e) {
            LOG.debug("SSE client gone, stopping stream: {}", e.toString());
            return false;
        }
    }

    /** SSE frame: an {@code event:} line, one {@code data:} line per source line, and a blank separator. */
    private static void writeEvent(HttpServerResponse aResponse, String aName, String aHtml) {
        StringBuilder sb = new StringBuilder();
        sb.append("event: ").append(aName).append('\n');
        for (String line : aHtml.split("\n", -1)) {
            sb.append("data: ").append(line).append('\n');
        }
        sb.append('\n');
        aResponse.write(sb.toString());
    }

    private static Buffer readResource(String aPath) {
        try (InputStream in = DashboardHttpHandler.class.getResourceAsStream(aPath)) {
            if (in == null) {
                throw new IllegalStateException("dashboard resource not found on classpath: " + aPath);
            }
            return Buffer.buffer(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read dashboard resource " + aPath, e);
        }
    }
}
