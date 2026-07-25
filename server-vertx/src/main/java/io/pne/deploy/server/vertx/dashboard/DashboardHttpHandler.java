package io.pne.deploy.server.vertx.dashboard;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.service.impl.alias.AliasDescription;
import io.pne.deploy.server.vertx.AgentConnections;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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
    private static final Pattern ALIAS_NAME   = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final Vertx                        vertx;
    private final AgentConnections             agents;
    private final Collection<Long>             pendingIssues;
    private final Map<String, PersistentSpool> queues;
    private final Supplier<TaskStatus>         taskStatusSupplier;
    private final MeterRegistry                registry; // nullable: no latency card without it
    private final AgentLogBuffer               logBuffer;
    private final List<StartupConfigReport.Entry> configEntries;
    private final File                         aliasesDir;
    private final long                         refreshMs;
    private final String                       redmineBaseUrl; // for linking issue ids on the task card
    private final String                       serverLogFile;  // tailed by the Log screen; blank disables it

    private final String basePath;
    private final String eventsPath;
    private final String htmxPath;
    private final String sseJsPath;
    private final String issuePath;
    private final String configPath;
    private final String aliasesPath;
    private final String aliasPath;
    private final String logEventsPath;

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
            , AgentLogBuffer aLogBuffer
            , List<StartupConfigReport.Entry> aConfigEntries
            , File aAliasesDir
            , String aBasePath
            , long aRefreshMs
            , String aServerLogFile
    ) {
        this.vertx              = aVertx;
        this.agents             = aAgents;
        this.pendingIssues      = aPendingIssues;
        this.queues             = aQueues;
        this.taskStatusSupplier = aTaskStatusSupplier;
        this.registry           = aRegistry;
        this.logBuffer          = aLogBuffer;
        this.configEntries      = aConfigEntries;
        this.aliasesDir         = aAliasesDir;
        this.refreshMs          = aRefreshMs;
        this.redmineBaseUrl     = findConfig(aConfigEntries, "REDMINE_URL");
        this.serverLogFile      = aServerLogFile;

        this.basePath    = normalize(aBasePath);
        this.eventsPath  = basePath + "/events";
        this.htmxPath    = basePath + "/htmx.min.js";
        this.sseJsPath   = basePath + "/sse.js";
        this.issuePath   = basePath + "/issue";
        this.configPath  = basePath + "/config";
        this.aliasesPath = basePath + "/aliases";
        this.aliasPath   = basePath + "/alias";
        this.logEventsPath = basePath + "/log/events";

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
        } else if (configPath.equals(path)) {
            serve(aRequest, "text/html; charset=utf-8", Buffer.buffer(DashboardView.config(configEntries)));
        } else if (aliasesPath.equals(path)) {
            handleAliasList(aRequest);
        } else if (aliasPath.equals(path)) {
            handleAliasDetail(aRequest, aRequest.getParam("name"));
        } else if (logEventsPath.equals(path)) {
            handleLogEvents(aRequest);
        } else if (basePath.equals(path) || (basePath + "/").equals(path)) {
            serve(aRequest, "text/html; charset=utf-8", indexHtml);
        } else {
            aRequest.response().setStatusCode(404).end("Not found\n");
        }
    }

    private void handleAliasList(HttpServerRequest aRequest) {
        List<DashboardView.AliasInfo> aliases = new ArrayList<>();
        String[] files = aliasesDir == null ? null : aliasesDir.list((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                String name = file.substring(0, file.length() - ".yml".length());
                aliases.add(new DashboardView.AliasInfo(name, commandCount(new File(aliasesDir, file))));
            }
        }
        serve(aRequest, "text/html; charset=utf-8", Buffer.buffer(DashboardView.aliasSidebar(aliases, basePath)));
    }

    private static int commandCount(File aFile) {
        try {
            AliasDescription description = new Yaml().loadAs(Files.readString(aFile.toPath()), AliasDescription.class);
            return description == null || description.commands == null ? 0 : description.commands.size();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private void handleAliasDetail(HttpServerRequest aRequest, String aName) {
        if (aliasesDir == null || aName == null || !ALIAS_NAME.matcher(aName).matches()) {
            aRequest.response().setStatusCode(400).end("bad alias name\n");
            return;
        }
        try {
            File file = new File(aliasesDir, aName + ".yml");
            // defence-in-depth against traversal, even though ALIAS_NAME already forbids separators
            if (!file.getCanonicalPath().startsWith(aliasesDir.getCanonicalPath() + File.separator)) {
                aRequest.response().setStatusCode(400).end("bad alias name\n");
                return;
            }
            if (!file.isFile()) {
                serve(aRequest, "text/html; charset=utf-8", Buffer.buffer("<p class=\"muted\">alias not found</p>"));
                return;
            }
            String raw = Files.readString(file.toPath());
            AliasDescription description = new Yaml().loadAs(raw, AliasDescription.class);
            serve(aRequest, "text/html; charset=utf-8", Buffer.buffer(DashboardView.aliasDetail(aName, description, raw)));
        } catch (IOException | RuntimeException e) {
            LOG.warn("cannot read alias {}", aName, e);
            serve(aRequest, "text/html; charset=utf-8", Buffer.buffer("<p class=\"pill bad\">cannot read alias</p>"));
        }
    }

    private static void serve(HttpServerRequest aRequest, String aContentType, Buffer aBody) {
        aRequest.response().putHeader("Content-Type", aContentType).end(aBody);
    }

    /** Effective value of a non-masked config entry by name, or "" when absent or masked. */
    private static String findConfig(List<StartupConfigReport.Entry> aEntries, String aName) {
        if (aEntries == null) {
            return "";
        }
        for (StartupConfigReport.Entry entry : aEntries) {
            if (aName.equals(entry.name()) && !entry.masked()) {
                return entry.value() == null ? "" : entry.value();
            }
        }
        return "";
    }

    /**
     * {base}/issue with {@code issue_id} in the query string or the url-encoded body -> enqueue, then return
     * the refreshed issues fragment. Method-agnostic on purpose: the reverse proxy in front of the dashboard
     * rewrites the htmx POST into a GET (keeping the body), so requiring POST would 405 every "Add".
     */
    private void handleIssue(HttpServerRequest aRequest) {
        aRequest.bodyHandler(aBody -> {
            String raw = aRequest.getParam("issue_id"); // query string, if any
            if (raw == null || raw.isBlank()) {
                raw = formField(aBody.toString(UTF_8), "issue_id"); // url-encoded body
            }
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
            aRequest.response()
                    .putHeader("Content-Type", "text/html; charset=utf-8")
                    .putHeader("Cache-Control", "no-store")
                    .end(body);
        });
    }

    /** First value of {@code aName} in an application/x-www-form-urlencoded body, URL-decoded; null if absent. */
    private static String formField(String aBody, String aName) {
        if (aBody == null || aBody.isEmpty()) {
            return null;
        }
        for (String pair : aBody.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq >= 0 ? pair.substring(0, eq) : pair, UTF_8);
            if (aName.equals(key)) {
                return eq >= 0 ? URLDecoder.decode(pair.substring(eq + 1), UTF_8) : "";
            }
        }
        return null;
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

    /** SSE tail of the configured server log file: the last 300 lines on connect, then appended lines each tick. */
    private void handleLogEvents(HttpServerRequest aRequest) {
        HttpServerResponse response = aRequest.response();
        response.setChunked(true);
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");

        if (serverLogFile == null || serverLogFile.isBlank()) {
            writeEvent(response, "logline", "<div class=\"log-row\">server log is not configured (SERVER_LOG_FILE)</div>");
            response.end();
            return;
        }

        ServerLogTailer tailer = new ServerLogTailer(new File(serverLogFile));
        long[] offset = {0};

        vertx.<List<String>>executeBlocking(() -> {
            List<String> initial = tailer.lastLines(300);
            offset[0] = tailer.size();
            return initial;
        }, false).onComplete(ar -> {
            if (ar.succeeded() && !response.closed() && !response.ended()) {
                writeEvent(response, "logline", logRows(ar.result()));
            }
        });

        long timerId = vertx.setPeriodic(refreshMs, id -> {
            if (response.closed() || response.ended()) {
                vertx.cancelTimer(id);
                return;
            }
            vertx.<ServerLogTailer.Chunk>executeBlocking(() -> tailer.readFrom(offset[0]), false).onComplete(ar -> {
                if (ar.failed()) {
                    return;
                }
                ServerLogTailer.Chunk chunk = ar.result();
                offset[0] = chunk.offset();
                if (!chunk.lines().isEmpty() && !response.closed() && !response.ended()) {
                    writeEvent(response, "logline", logRows(chunk.lines()));
                }
            });
        });

        aRequest.connection().closeHandler(aVoid -> vertx.cancelTimer(timerId));
    }

    private static String logRows(List<String> aLines) {
        StringBuilder sb = new StringBuilder();
        for (String line : aLines) {
            sb.append("<div class=\"log-row\">").append(DashboardView.esc(line)).append("</div>");
        }
        return sb.toString();
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
            writeEvent(aResponse, "status", DashboardView.status(taskStatusSupplier.get(), redmineBaseUrl));
            writeEvent(aResponse, "issues", DashboardView.issues(issueSnapshot));
            writeEvent(aResponse, "delivery", DashboardView.delivery(queues, latencySnapshot()));
            writeEvent(aResponse, "logs", DashboardView.logs(logBuffer.snapshot(50)));
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
