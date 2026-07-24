package io.pne.deploy.tests.env;

import io.pne.deploy.agent.websocket.WebSocketAgentApplication;
import io.pne.deploy.server.vertx.VertxServerApplication;
import io.pne.deploy.tests.TestAgentApplicationListener;
import io.pne.deploy.tests.TestAgentStartupParameters;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Boots the ENTIRE deploy environment in one process — the deploy-server, two websocket deploy-agents,
 * and HTTP mocks for Redmine, GitLab and Telegram — wired together via system properties (which the
 * {@code @AStartupParameter} config layer reads before environment variables). Runnable from a
 * {@link #main(String[])} (IntelliJ, or {@code mvn exec:java}) and reusable from tests.
 *
 * <p>The mocks are just enough to drive one Redmine issue through the full pipeline:
 * getIssue → Nashorn validation → GitLab diff → deploy on both agents → Redmine status updates +
 * Telegram notifications.
 */
public class LocalEnvironment implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LocalEnvironment.class);

    private static final String TOKEN        = "test-token";
    private static final String ALIAS        = "deploy-demo";
    private static final String CALLBACK_URI = "/redmine/callback";
    // The line the pipeline looks for in the issue description (alias-line chars are restricted: no '/', ':', '=').
    private static final String DEPLOY_LINE = "> deploy " + ALIAS;

    private final int serverPort   = Ports.free();
    private final int redminePort  = Ports.free();
    private final int gitlabPort   = Ports.free();
    private final int telegramPort = Ports.free();

    private final Vertx mockVertx = Vertx.vertx();

    private final List<String> setProps = new ArrayList<>();

    private HttpMock redmine;
    private HttpMock gitlab;
    private HttpMock telegram;

    private VertxServerApplication server;

    private final List<WebSocketAgentApplication> agents    = new ArrayList<>();
    private final List<ExecutorService>           executors = new ArrayList<>();

    private Path aliasesDir;
    private Path queueDir;
    private Path validationScript;

    public void start() throws Exception {
        LOG.info("Starting local environment: server={} redmine={} gitlab={} telegram={}",
                serverPort, redminePort, gitlabPort, telegramPort);

        startMocks();
        writeTempFiles();
        applySystemProperties();

        server = new VertxServerApplication();
        server.start();
        waitForServer();

        startAgent("agent-1");
        startAgent("agent-2");

        LOG.info("Local environment is up.");
    }

    private void startMocks() {
        redmine = new HttpMock(mockVertx, "redmine", redminePort, (req, body) -> {
            String path = req.path();
            if (req.method() == HttpMethod.GET && path.matches("/issues/\\d+\\.json")) {
                String id = path.replaceAll("\\D", "");
                req.response().putHeader("Content-Type", "application/json").end(issueJson(id));
            } else if (req.method() == HttpMethod.PUT && path.matches("/issues/\\d+\\.json")) {
                req.response().setStatusCode(204).end();
            } else {
                req.response().setStatusCode(404).end("not found\n");
            }
        });

        gitlab = new HttpMock(mockVertx, "gitlab", gitlabPort, (req, body) -> {
            String path = req.path();
            if (path.equals("/old-version")) {
                req.response().end("1.2.2\n"); // first line = old version
            } else if (path.contains("/repository/compare")) {
                req.response().putHeader("Content-Type", "application/json")
                        .end("{\"commits\":[{\"message\":\"deploy demo build\"}]}");
            } else {
                req.response().setStatusCode(404).end("not found\n");
            }
        });

        telegram = new HttpMock(mockVertx, "telegram", telegramPort, (req, body) ->
                req.response().putHeader("Content-Type", "application/json")
                        .end("{\"ok\":true,\"result\":{\"message_id\":1}}"));
    }

    private static String issueJson(String aId) {
        return "{\"issue\":{"
                + "\"id\":" + aId + ","
                + "\"subject\":\"demo deploy\","
                + "\"description\":\"" + DEPLOY_LINE + "\","
                + "\"status\":{\"id\":2,\"name\":\"In Progress\"},"
                + "\"project\":{\"id\":1,\"name\":\"demo\"},"
                + "\"author\":{\"id\":1,\"name\":\"author\"},"
                + "\"assigned_to\":{\"id\":2,\"name\":\"assignee\"},"
                + "\"custom_fields\":[]"
                + "}}";
    }

    private void writeTempFiles() {
        try {
            aliasesDir       = Files.createTempDirectory("deploy-env-aliases");
            queueDir         = Files.createTempDirectory("deploy-env-queue");
            validationScript = Files.createTempFile("issue-validation", ".js");
            Files.writeString(validationScript, "true;\n", StandardCharsets.UTF_8);

            String oldVersionUrl = "http://127.0.0.1:" + gitlabPort + "/old-version";
            String yaml = ""
                    + "commands:\n"
                    + "- agents: agent-1\n"
                    + "  name: echo\n"
                    + "  arguments:\n"
                    + "    - deployed\n"
                    + "    - 1.2.3\n"
                    + "    - " + oldVersionUrl + "\n"
                    + "    - gitlab=42\n"
                    + "- agents: agent-2\n"
                    + "  name: echo\n"
                    + "  arguments:\n"
                    + "    - deployed\n"
                    + "    - 1.2.3\n"
                    + "    - " + oldVersionUrl + "\n"
                    + "    - gitlab=42\n";
            Files.writeString(aliasesDir.resolve(ALIAS + ".yml"), yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write temp environment files", e);
        }
    }

    private void applySystemProperties() {
        set("VERTX_SERVER_PORT",       Integer.toString(serverPort));
        set("VERTX_ALIASES_DIR",       aliasesDir.toString());
        set("REDMINE_URL",             "http://127.0.0.1:" + redminePort);
        set("REDMINE_API_ACCESS_KEY",  "test-key");
        set("REDMINE_CALLBACK_URI",    CALLBACK_URI);
        set("ISSUE_VALIDATION_SCRIPT", validationScript.toString());
        set("QUEUE_DIR",               queueDir.toString());
        set("GITLAB_URL",              "http://127.0.0.1:" + gitlabPort);
        set("GITLAB_API_KEY",          "test-gitlab-key");
        set("TELEGRAM_ENABLED",        "true");
        set("TELEGRAM_TOKEN",          TOKEN);
        set("TELEGRAM_CHAT_ID",        "1");
        set("TELEGRAM_URL",            "http://127.0.0.1:" + telegramPort + "/bot");
        set("DASHBOARD_REFRESH_MS",    "1000");
    }

    private void set(String aName, String aValue) {
        System.setProperty(aName, aValue);
        setProps.add(aName);
    }

    private void waitForServer() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/deploy/status"))
                .timeout(Duration.ofSeconds(2)).GET().build();
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // server not up yet
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("deploy-server did not become ready on " + baseUrl());
    }

    private void startAgent(String aAgentId) throws InterruptedException {
        TestAgentApplicationListener listener = new TestAgentApplicationListener();
        WebSocketAgentApplication agent = new WebSocketAgentApplication(listener,
                new TestAgentStartupParameters(baseUrl() + "/", aAgentId));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-" + aAgentId);
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(agent::start);
        agents.add(agent);
        executors.add(executor);
        if (!listener.waitUntilConnected(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("agent " + aAgentId + " did not connect");
        }
        LOG.info("Agent {} connected", aAgentId);
    }

    /** Enqueues a Redmine issue for processing by hitting the Redmine webhook callback with a JSON body. */
    public void triggerIssue(long aIssueId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + CALLBACK_URI))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"issue_id\":" + aIssueId + "}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        LOG.info("triggerIssue({}) -> {} {}", aIssueId, response.statusCode(), response.body().trim());
    }

    /** Opens the dashboard SSE stream and returns the frames read until {@code until} appears or timeout. */
    public String readDashboardEvents(String aUntil, long aTimeoutMs) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/deploy/dashboard/events")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        InputStream in = response.body();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<String> future = reader.submit(() -> {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                if (sb.indexOf(aUntil) >= 0) {
                    return sb.toString();
                }
            }
            return sb.toString();
        });
        try {
            return future.get(aTimeoutMs, TimeUnit.MILLISECONDS);
        } finally {
            in.close();
            reader.shutdownNow();
        }
    }

    /** Simple blocking GET of a dashboard path; returns the response body. */
    public String httpGet(String aPath) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + aPath)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + serverPort;
    }

    public int serverPort()   { return serverPort; }
    public HttpMock redmine()  { return redmine; }
    public HttpMock gitlab()   { return gitlab; }
    public HttpMock telegram() { return telegram; }

    @Override
    public void close() {
        stop();
    }

    public void stop() {
        for (WebSocketAgentApplication agent : agents) {
            try {
                agent.stop();
            } catch (RuntimeException e) {
                LOG.warn("agent stop failed", e);
            }
        }
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
        }
        agents.clear();
        executors.clear();

        if (server != null) {
            try {
                server.stop();
            } catch (RuntimeException e) {
                LOG.warn("server stop failed", e);
            }
            server = null;
        }

        stopQuietly(redmine);
        stopQuietly(gitlab);
        stopQuietly(telegram);
        redmine = gitlab = telegram = null;

        CountDownLatch closed = new CountDownLatch(1);
        mockVertx.close(ar -> closed.countDown());
        try {
            closed.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clear our system properties so they cannot leak into other tests in the same JVM.
        for (String name : setProps) {
            System.clearProperty(name);
        }
        setProps.clear();

        deleteRecursively(queueDir);
        deleteRecursively(aliasesDir);
        deleteQuietly(validationScript);

        LOG.info("Local environment stopped.");
    }

    private static void stopQuietly(HttpMock aMock) {
        if (aMock != null) {
            try {
                aMock.stop();
            } catch (RuntimeException e) {
                LOG.warn("mock {} stop failed", aMock.name(), e);
            }
        }
    }

    private static void deleteRecursively(Path aDir) {
        if (aDir == null) {
            return;
        }
        try (var walk = Files.walk(aDir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(LocalEnvironment::deleteQuietly);
        } catch (IOException e) {
            LOG.debug("cannot clean {}", aDir, e);
        }
    }

    private static void deleteQuietly(Path aPath) {
        if (aPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(aPath);
        } catch (IOException ignored) {
            // best effort
        }
    }

    public static void main(String[] args) throws Exception {
        LocalEnvironment env = new LocalEnvironment();
        Runtime.getRuntime().addShutdownHook(new Thread(env::stop, "env-shutdown"));
        env.start();

        String base = env.baseUrl();
        System.out.println();
        System.out.println("=========================================================");
        System.out.println(" Deploy environment is UP");
        System.out.println("   Server      : " + base);
        System.out.println("   Dashboard   : " + base + "/deploy/dashboard");
        System.out.println("   Status JSON : " + base + "/deploy/status");
        System.out.println("   Metrics     : " + base + "/metrics");
        System.out.println("   Agents      : agent-1, agent-2 (connected)");
        System.out.println("   Redmine mock: http://127.0.0.1:" + env.redmine().port());
        System.out.println("   GitLab mock : http://127.0.0.1:" + env.gitlab().port());
        System.out.println("   Telegram mock: http://127.0.0.1:" + env.telegram().port());
        System.out.println("   Trigger     : " + base + "/?command=issue&issue_id=1001");
        System.out.println("=========================================================");
        System.out.println();

        env.triggerIssue(1001);

        // Keep running so the dashboard can be inspected; Ctrl-C triggers the shutdown hook.
        new CountDownLatch(1).await();
    }
}
