package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.payneteasy.http.client.api.HttpHeader;
import com.payneteasy.http.client.api.HttpMethod;
import com.payneteasy.http.client.api.HttpRequest;
import com.payneteasy.http.client.api.HttpResponse;
import com.payneteasy.http.client.api.IHttpClient;
import io.pne.deploy.client.redmine.remote.data_model.DeployReviewRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RemoteDeployReviewServiceImplTest {

    private static final String URL   = "https://review.example.com/deploy-review";
    private static final String TOKEN = "test-token";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final IHttpClient client = mock(IHttpClient.class);

    @Test
    public void postsBearerTokenAndSnakeCaseBody() throws Exception {
        File dir = tmp.newFolder("accepted");
        when(client.send(any(), any())).thenReturn(response(202, "{\"status\":\"accepted\",\"task_id\":\"t-1\"}"));
        AtomicLong latency = new AtomicLong(-1);

        service(dir, latency::set).enqueueDeployStarted(request());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, timeout(2000)).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();

        assertEquals(URL, sent.getUrl());
        assertEquals(HttpMethod.POST, sent.getMethod());
        assertEquals("Bearer " + TOKEN, header(sent, "Authorization"));
        assertEquals("application/json", header(sent, "Content-Type"));

        JsonObject body = JsonParser.parseString(new String(sent.getBody(), UTF_8)).getAsJsonObject();
        assertEquals("payneteasy/paynet", body.get("project").getAsString());
        assertEquals("ams2-paynet-proc", body.get("app").getAsString());
        assertEquals("AMS-2", body.get("instance").getAsString());
        assertEquals("3.36.16-117", body.get("old_version").getAsString());
        assertEquals("3.36.16-118", body.get("new_version").getAsString());
        assertTrue("deployed_at must be ISO-8601 UTC with second precision: " + body.get("deployed_at"),
                body.get("deployed_at").getAsString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));

        awaitSpoolEmpty(dir);
        assertTrue("latency should be recorded and positive", latency.get() > 0);
        assertNull("nothing must be dead-lettered on 202", deadFiles(dir));
    }

    @Test
    public void keepsCallerSuppliedDeployedAt() throws Exception {
        when(client.send(any(), any())).thenReturn(response(202, "{\"status\":\"duplicate\"}"));
        DeployReviewRequest request = request();
        request.setDeployedAt("2026-08-11T15:07:39Z");

        service(tmp.newFolder("supplied"), null).enqueueDeployStarted(request);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, timeout(2000)).send(captor.capture(), any());
        JsonObject body = JsonParser.parseString(new String(captor.getValue().getBody(), UTF_8)).getAsJsonObject();
        assertEquals("2026-08-11T15:07:39Z", body.get("deployed_at").getAsString());
    }

    @Test
    public void retriesOnServerErrorThenSucceeds() throws Exception {
        File dir = tmp.newFolder("retry");
        when(client.send(any(), any()))
                .thenReturn(response(503, "upstream down"))
                .thenReturn(response(202, "{\"status\":\"accepted\"}"));

        service(dir, null).enqueueDeployStarted(request());

        verify(client, timeout(5000).times(2)).send(any(), any()); // first Backoff delay is 1s
        awaitSpoolEmpty(dir);
        assertNull("a retried-then-accepted notification must not be dead-lettered", deadFiles(dir));
    }

    @Test
    public void deadLettersUnauthorizedWithoutRetry() throws Exception {
        File dir = tmp.newFolder("unauthorized");
        when(client.send(any(), any())).thenReturn(response(401, "{\"detail\":\"bad token\"}"));

        service(dir, null).enqueueDeployStarted(request());

        verify(client, timeout(2000)).send(any(), any());
        verify(client, after(500).times(1)).send(any(), any()); // 401 never succeeds on a retry
        awaitSpoolEmpty(dir);
        assertEquals(1, deadFiles(dir).length);
    }

    @Test
    public void deadLettersUnprocessableEntityWithoutRetry() throws Exception {
        File dir = tmp.newFolder("unprocessable");
        when(client.send(any(), any())).thenReturn(response(422, "{\"detail\":\"bad version format\"}"));

        service(dir, null).enqueueDeployStarted(request());

        verify(client, timeout(2000)).send(any(), any());
        verify(client, after(500).times(1)).send(any(), any());
        assertEquals(1, deadFiles(dir).length);
    }

    @Test
    public void resendsPersistedNotificationOnStartup() throws Exception {
        File   dir  = tmp.newFolder("replay");
        String body = "{\"project\":\"payneteasy/paynet\",\"app\":\"ams2-paynet-proc\",\"instance\":\"AMS-2\""
                + ",\"old_version\":\"1\",\"new_version\":\"2\",\"deployed_at\":\"2026-08-11T15:07:39Z\"}";
        // persisted but not confirmed sent before a restart
        Files.write(new File(dir, "00000000000000000001.json").toPath(), body.getBytes(UTF_8));
        when(client.send(any(), any())).thenReturn(response(202, "{\"status\":\"accepted\"}"));

        service(dir, null); // constructing the service replays the spool

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, timeout(2000)).send(captor.capture(), any());
        // the spooled file is the exact request body, so the replayed deploy time is preserved
        assertEquals(body, new String(captor.getValue().getBody(), UTF_8));
        awaitSpoolEmpty(dir);
    }

    @Test
    public void blankUrlFailsFast() throws Exception {
        try {
            new RemoteDeployReviewServiceImpl("", TOKEN, 1_000, tmp.newFolder("no-url"), client, null);
            fail("expected a startup failure when DEPLOY_REVIEW_URL is not set");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("DEPLOY_REVIEW_URL"));
        }
    }

    private RemoteDeployReviewServiceImpl service(File aSpoolDir, java.util.function.LongConsumer aLatency) {
        return new RemoteDeployReviewServiceImpl(URL, TOKEN, 1_000, aSpoolDir, client, aLatency);
    }

    private static DeployReviewRequest request() {
        DeployReviewRequest request = new DeployReviewRequest();
        request.setProject("payneteasy/paynet");
        request.setApp("ams2-paynet-proc");
        request.setInstance("AMS-2");
        request.setOldVersion("3.36.16-117");
        request.setNewVersion("3.36.16-118");
        return request;
    }

    private static String header(HttpRequest aRequest, String aName) {
        for (HttpHeader httpHeader : aRequest.getHeaders().asList()) {
            if (aName.equals(httpHeader.getName())) {
                return httpHeader.getValue();
            }
        }
        return null;
    }

    private static File[] deadFiles(File aDir) {
        return new File(aDir, "dead").listFiles((d, n) -> n.endsWith(".json"));
    }

    /** The spool file is removed on the sender thread, just after the HTTP call the test already saw. */
    private static void awaitSpoolEmpty(File aDir) throws InterruptedException {
        for (int i = 0; i < 40 && aDir.listFiles((d, n) -> n.endsWith(".json")).length > 0; i++) {
            Thread.sleep(50);
        }
        assertEquals(0, aDir.listFiles((d, n) -> n.endsWith(".json")).length);
    }

    private static HttpResponse response(int aStatus, String aBody) {
        return new HttpResponse(aStatus, "", Collections.emptyList(), aBody.getBytes(UTF_8));
    }
}
