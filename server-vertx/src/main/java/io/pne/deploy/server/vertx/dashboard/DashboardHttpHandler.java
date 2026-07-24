package io.pne.deploy.server.vertx.dashboard;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.vertx.AgentConnections;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Serves the live dashboard: a static HTML shell plus the vendored htmx assets, an SSE stream at
 * {@code <base>/events} that pushes rendered HTML fragments (one named event per card) on a fixed
 * interval, and a {@code POST <base>/issue} action that enqueues a Redmine issue. No authentication.
 * The base path and refresh interval are configured via {@link IDashboardConfig}.
 */
public class DashboardHttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardHttpHandler.class);

    private static final String LATENCY_METER = "deploy_queue_send_latency";

    private final Vertx                        vertx;
    private final AgentConnections             agents;
    private final Collection<Long>             pendingIssues;
    private final Map<String, PersistentSpool> queues;
    private final Supplier<TaskStatus>         taskStatusSupplier;
    private final MeterRegistry                registry; // nullable: no latency card without it
    private final long                         refreshMs;

    private final String basePath;
    private final String eventsPath;
    private final String htmxPath;
    private final String sseJsPath;
    private final String issuePath;

    private final Buffer indexHtml;
    private final Buffer htmxJs = readResource("/dashboard/htmx.min.js");
    private final Buffer sseJs  = readResource("/dashboard/sse.js");

    public DashboardHttpHandler(
            Vertx aVertx
            , AgentConnections aAgents
            , Collection<Long> aPendingIssues
            , Map<String, PersistentSpool> aQueues
            , Supplier<TaskStatus> aTaskStatusSupplier
            , MeterRegistry aRegistry
            , String aBasePath
            , long aRefreshMs
    ) {
        this.vertx              = aVertx;
        this.agents             = aAgents;
        this.pendingIssues      = aPendingIssues;
        this.queues             = aQueues;
        this.taskStatusSupplier = aTaskStatusSupplier;
        this.registry           = aRegistry;
        this.refreshMs          = aRefreshMs;

        this.basePath   = normalize(aBasePath);
        this.eventsPath = basePath + "/events";
        this.htmxPath   = basePath + "/htmx.min.js";
        this.sseJsPath  = basePath + "/sse.js";
        this.issuePath  = basePath + "/issue";

        this.indexHtml = Buffer.buffer(readResourceString("/dashboard/index.html").replace("{{BASE}}", basePath));
    }

    /** Whether this handler owns the given request path (used by the front router). */
    public boolean handles(String aPath) {
        return aPath.equals(basePath) || aPath.startsWith(basePath + "/");
    }

    @Override
    public void handle(HttpServerRequest aRequest) {
        String path = aRequest.path();
        if (issuePath.equals(path)) {
            handleIssue(aRequest);
        } else if (eventsPath.equals(path)) {
            handleEvents(aRequest);
        } else if (htmxPath.equals(path)) {
            serve(aRequest, "application/javascript; charset=utf-8", htmxJs);
        } else if (sseJsPath.equals(path)) {
            serve(aRequest, "application/javascript; charset=utf-8", sseJs);
        } else if (basePath.equals(path) || (basePath + "/").equals(path)) {
            serve(aRequest, "text/html; charset=utf-8", indexHtml);
        } else {
            aRequest.response().setStatusCode(404).end("Not found\n");
        }
    }

    private static void serve(HttpServerRequest aRequest, String aContentType, Buffer aBody) {
        aRequest.response().putHeader("Content-Type", aContentType).end(aBody);
    }

    /** POST {base}/issue with form field issue_id -> enqueue, then return the refreshed issues fragment. */
    private void handleIssue(HttpServerRequest aRequest) {
        if (aRequest.method() != HttpMethod.POST) {
            aRequest.response().setStatusCode(405).end("Method not allowed\n");
            return;
        }
        aRequest.setExpectMultipart(true);
        aRequest.endHandler(aVoid -> {
            String raw   = aRequest.getFormAttribute("issue_id");
            String error = null;
            try {
                long id = Long.parseLong(raw == null ? "" : raw.trim());
                if (id <= 0) {
                    error = "issue id must be positive";
                } else {
                    pendingIssues.add(id); // bounded queue throws IllegalStateException when full
                }
            } catch (NumberFormatException e) {
                error = "invalid issue id: " + raw;
            } catch (IllegalStateException e) {
                error = "queue is full";
            }
            String body = (error == null ? "" : "<p class=\"pill bad\">" + DashboardView.esc(error) + "</p>")
                    + DashboardView.issues(new ArrayList<>(pendingIssues));
            aRequest.response().putHeader("Content-Type", "text/html; charset=utf-8").end(body);
        });
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
            Set<String>      agentSnapshot = new LinkedHashSet<>(agents.getAgentList());
            Collection<Long> issueSnapshot = new ArrayList<>(pendingIssues);
            Runtime          runtime       = Runtime.getRuntime();
            long             heapUsed      = runtime.totalMemory() - runtime.freeMemory();

            writeEvent(aResponse, "service", DashboardView.service(
                    ManagementFactory.getRuntimeMXBean().getUptime(), heapUsed, runtime.maxMemory(), agentSnapshot.size()));
            writeEvent(aResponse, "agents", DashboardView.agents(agentSnapshot));
            writeEvent(aResponse, "status", DashboardView.status(taskStatusSupplier.get()));
            writeEvent(aResponse, "issues", DashboardView.issues(issueSnapshot));
            writeEvent(aResponse, "queues", DashboardView.queues(queues));
            writeEvent(aResponse, "latency", DashboardView.latency(latencySnapshot()));
            return true;
        } catch (RuntimeException e) {
            LOG.debug("SSE client gone, stopping stream: {}", e.toString());
            return false;
        }
    }

    /** Reads current send-latency percentiles per queue from the registry (empty if no registry/timer). */
    private Map<String, LatencyStat> latencySnapshot() {
        Map<String, LatencyStat> stats = new LinkedHashMap<>();
        if (registry == null) {
            return stats;
        }
        for (String queue : queues.keySet()) {
            Timer timer = registry.find(LATENCY_METER).tag("queue", queue).timer();
            if (timer == null) {
                continue;
            }
            HistogramSnapshot snap = timer.takeSnapshot();
            double p50 = 0, p95 = 0, p99 = 0;
            for (ValueAtPercentile v : snap.percentileValues()) {
                double ms = v.value(TimeUnit.MILLISECONDS);
                if (Math.abs(v.percentile() - 0.5) < 1e-6) {
                    p50 = ms;
                } else if (Math.abs(v.percentile() - 0.95) < 1e-6) {
                    p95 = ms;
                } else if (Math.abs(v.percentile() - 0.99) < 1e-6) {
                    p99 = ms;
                }
            }
            stats.put(queue, new LatencyStat(
                    snap.count(), snap.mean(TimeUnit.MILLISECONDS), p50, p95, p99, snap.max(TimeUnit.MILLISECONDS)));
        }
        return stats;
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

    private static String normalize(String aBasePath) {
        String path = aBasePath == null || aBasePath.isEmpty() ? "/deploy/dashboard" : aBasePath.trim();
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static Buffer readResource(String aPath) {
        return Buffer.buffer(readResourceBytes(aPath));
    }

    private static String readResourceString(String aPath) {
        return new String(readResourceBytes(aPath), UTF_8);
    }

    private static byte[] readResourceBytes(String aPath) {
        try (InputStream in = DashboardHttpHandler.class.getResourceAsStream(aPath)) {
            if (in == null) {
                throw new IllegalStateException("dashboard resource not found on classpath: " + aPath);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read dashboard resource " + aPath, e);
        }
    }
}
