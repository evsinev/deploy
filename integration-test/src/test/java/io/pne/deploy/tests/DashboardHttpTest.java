package io.pne.deploy.tests;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.vertx.IVertxServerConfiguration;
import io.pne.deploy.server.vertx.VertxServerApplication;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DashboardHttpTest {

    private static final int    PORT = 9093;
    private static final String BASE = "http://127.0.0.1:" + PORT;

    @Test
    public void dashboardServesPageAssetsAndStreamsSse() throws Exception {
        TestServerApplicationListener serverListener = new TestServerApplicationListener();
        VertxServerApplication server = new VertxServerApplication(serverListener, config(),
                StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class));
        HttpClient client = HttpClient.newHttpClient();
        try {
            server.start();
            assertTrue("server did not start in time", serverListener.waitUntilStarted(5, SECONDS));

            // 1. the HTML shell
            HttpResponse<String> page = client.send(get("/deploy/dashboard"), ofString());
            assertEquals(200, page.statusCode());
            assertTrue("content-type: " + page.headers().firstValue("content-type"),
                    page.headers().firstValue("content-type").orElse("").startsWith("text/html"));
            assertTrue("page should wire up SSE", page.body().contains("sse-connect"));

            // 2. the vendored htmx asset
            HttpResponse<String> htmx = client.send(get("/deploy/dashboard/htmx.min.js"), ofString());
            assertEquals(200, htmx.statusCode());
            assertTrue(htmx.headers().firstValue("content-type").orElse("").contains("javascript"));
            assertTrue(htmx.body().contains("htmx"));

            // 3. the issue action enqueues and returns the refreshed list
            HttpResponse<String> added = client.send(
                    HttpRequest.newBuilder(URI.create(BASE + "/deploy/dashboard/issue"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString("issue_id=777"))
                            .build(),
                    ofString());
            assertEquals(200, added.statusCode());
            assertTrue("issue list should show the added id, got: " + added.body(), added.body().contains("#777"));

            // 3a. the id may also arrive on the query string (GET) — must enqueue, not 405
            HttpResponse<String> viaQuery = client.send(get("/deploy/dashboard/issue?issue_id=555"), ofString());
            assertEquals(200, viaQuery.statusCode());
            assertTrue("query-string id should enqueue, got: " + viaQuery.body(), viaQuery.body().contains("#555"));

            // 3b. a reverse proxy rewrites the htmx POST into a GET keeping the body; that must still enqueue
            String rewritten = issueViaRawGetWithBody("issue_id=888");
            assertTrue("proxy-rewritten GET must not be 405, got: " + rewritten, rewritten.startsWith("HTTP/1.1 200"));
            assertTrue("GET with body should enqueue the id, got: " + rewritten, rewritten.contains("#888"));

            // 3c. the Log screen initial content always renders a reason instead of a silent blank
            HttpResponse<String> log = client.send(get("/deploy/dashboard/log"), ofString());
            assertEquals(200, log.statusCode());
            assertTrue("log should explain why it is empty, got: " + log.body(),
                    log.body().contains("server log is not configured"));

            // 4. the live SSE stream — first snapshot (all cards) must arrive immediately
            HttpResponse<InputStream> events = client.send(get("/deploy/dashboard/events"), ofInputStream());
            assertEquals(200, events.statusCode());
            assertTrue(events.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"));
            String frames = readUntil(events.body(), "event: logs"); // last event in a snapshot
            assertTrue("SSE should push an 'agents' event, got: " + frames, frames.contains("event: agents"));
            assertTrue("SSE should push a merged 'delivery' event, got: " + frames, frames.contains("event: delivery"));
            assertTrue("SSE frames should carry data lines", frames.contains("data:"));
        } finally {
            server.stop();
        }
    }

    private static HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(BASE + path)).GET().build();
    }

    /**
     * Sends a raw {@code GET} carrying a url-encoded body — exactly what the production reverse proxy does
     * when it rewrites the htmx POST — and returns the full HTTP response. Java's {@link HttpClient} forbids
     * a body on GET, hence the raw socket.
     */
    private static String issueViaRawGetWithBody(String aBody) throws Exception {
        byte[] payload = aBody.getBytes(UTF_8);
        String head = "GET /deploy/dashboard/issue HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + PORT + "\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket("127.0.0.1", PORT)) {
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(UTF_8));
            out.write(payload);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), UTF_8);
        }
    }

    private static IVertxServerConfiguration config() {
        return new IVertxServerConfiguration() {
            @Override
            public int getPort() {
                return PORT;
            }

            @Override
            public File getAliasesDir() {
                return new File("../server/src/test/resources/aliases");
            }
        };
    }

    /** Reads the (open) SSE stream until {@code marker} appears or 5s elapse, then closes it. */
    private static String readUntil(InputStream in, String marker) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<String> future = ex.submit(() -> {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, UTF_8));
                if (sb.indexOf(marker) >= 0) {
                    return sb.toString();
                }
            }
            return sb.toString();
        });
        try {
            return future.get(5, SECONDS);
        } finally {
            in.close();
            ex.shutdownNow();
        }
    }
}
