package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.http.client.api.*;
import com.payneteasy.http.client.impl.HttpClientImpl;
import io.pne.deploy.client.redmine.remote.IRemoteDeployReviewService;
import io.pne.deploy.client.redmine.remote.data_model.DeployReviewRequest;
import io.pne.deploy.client.redmine.remote.queue.Backoff;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Sends the deploy-start webhook through a durable spool, mirroring {@link RemoteRedmine4_2_10ServiceImpl}:
 * the request body is written to disk before the call and removed after a success, so a restart replays
 * whatever was not confirmed. Delivery is at-least-once — the receiver dedupes by version, answering
 * {@code 202 {"status":"duplicate"}} for a repeat.
 */
public class RemoteDeployReviewServiceImpl implements IRemoteDeployReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDeployReviewServiceImpl.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER               = "Bearer ";
    private static final String CONTENT_TYPE         = "Content-Type";
    private static final String APPLICATION_JSON     = "application/json";

    private final String                url;
    private final String                token;
    private final IHttpClient           client;
    private final Gson                  gson = new GsonBuilder().disableHtmlEscaping().create();
    private final HttpRequestParameters requestParameters;

    /** Webhook calls are serialized off the deploy thread through this queue. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "deploy-review-sender");
        thread.setDaemon(true);
        return thread;
    });

    private final PersistentSpool spool;
    private final LongConsumer    sendLatencyNanos; // nullable: records duration of a successful call

    public RemoteDeployReviewServiceImpl(IDeployReviewConfig aConfig, File aSpoolDir, LongConsumer aSendLatencyNanos) {
        this(aConfig.url(), aConfig.token(), aConfig.timeoutMs(), aSpoolDir, new HttpClientImpl(), aSendLatencyNanos);
    }

    /** Injectable constructor for tests (mock the HTTP client). */
    RemoteDeployReviewServiceImpl(String aUrl, String aToken, int aTimeoutMs, File aSpoolDir
            , IHttpClient aClient, LongConsumer aSendLatencyNanos) {
        if (aUrl == null || aUrl.isBlank()) {
            throw new IllegalStateException("DEPLOY_REVIEW_URL is not set but the deploy-review webhook is enabled");
        }
        url               = aUrl;
        token             = aToken;
        client            = aClient;
        sendLatencyNanos  = aSendLatencyNanos;
        requestParameters = HttpRequestParameters.builder().timeouts(new HttpTimeouts(aTimeoutMs, aTimeoutMs)).build();
        spool             = new PersistentSpool(aSpoolDir);
        // resend notifications that survived a restart (were persisted but not confirmed sent)
        for (PersistentSpool.Stored stored : spool.loadAll()) {
            writer.submit(() -> runOp(stored.getFileName(), stored.getJson()));
        }
    }

    @Override
    public void enqueueDeployStarted(DeployReviewRequest aRequest) {
        if (aRequest.getDeployedAt() == null || aRequest.getDeployedAt().isBlank()) {
            // stamped at enqueue time so a retry or a post-restart replay still reports the real deploy moment
            aRequest.setDeployedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)));
        }
        LOG.info("enqueueDeployStarted(project={}, app={}, instance={}, {} -> {})"
                , aRequest.getProject(), aRequest.getApp(), aRequest.getInstance()
                , aRequest.getOldVersion(), aRequest.getNewVersion());
        // the spooled file is the exact request body, so a replay needs no DTO round-trip
        String body = gson.toJson(aRequest);
        String file = spool.append(body);
        writer.submit(() -> runOp(file, body));
    }

    public PersistentSpool getSpool() {
        return spool;
    }

    private void runOp(String aFile, String aJsonBody) {
        for (int attempt = 1; attempt <= Backoff.MAX_ATTEMPTS; attempt++) {
            try {
                long start = System.nanoTime();
                send(aJsonBody);
                recordLatency(start);
                spool.remove(aFile);
                return;
            } catch (PermanentFailureException e) {
                LOG.error("deploy-review notification rejected, dead-lettering without retry: {}", e.getMessage());
                spool.deadLetter(aFile);
                return;
            } catch (Exception e) {
                LOG.warn("deploy-review notification failed (attempt {}/{})", attempt, Backoff.MAX_ATTEMPTS, e);
                if (attempt < Backoff.MAX_ATTEMPTS) {
                    sleep(Backoff.delayMs(attempt));
                }
            }
        }
        LOG.error("deploy-review notification dead-lettered after {} attempts", Backoff.MAX_ATTEMPTS);
        spool.deadLetter(aFile);
    }

    private void send(String aJsonBody) {
        // the token travels in the Authorization header and is never logged
        HttpRequest request = HttpRequest.builder()
                .url(url)
                .headers(new HttpHeaders(Arrays.asList(
                          new HttpHeader(AUTHORIZATION_HEADER, BEARER + token)
                        , new HttpHeader(CONTENT_TYPE, APPLICATION_JSON)
                )))
                .method(HttpMethod.POST)
                .body(aJsonBody.getBytes(UTF_8))
                .build();

        HttpResponse response;
        try {
            response = client.send(request, requestParameters);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send deploy-review notification to " + url, e);
        }

        int    status = response.getStatusCode();
        String body   = new String(response.getBody(), UTF_8);

        if (status >= 200 && status < 300) {
            LOG.info("deploy-review notification accepted: HTTP {}, status '{}'", status, parseStatus(body));
            return;
        }
        // 401 (bad token) and 422 (bad version/date format) never succeed on a retry
        if (status >= 400 && status < 500) {
            throw new PermanentFailureException("HTTP " + status + " from " + url + ": " + snippet(body));
        }
        throw new IllegalStateException("deploy-review HTTP " + status + " from " + url + ": " + snippet(body));
    }

    /** The {@code status} field of a 2xx body ({@code accepted} / {@code duplicate} / {@code skipped}), for logs only. */
    private String parseStatus(String aBody) {
        try {
            DeployReviewResponse parsed = gson.fromJson(aBody, DeployReviewResponse.class);
            return parsed == null || parsed.status == null ? "" : parsed.status;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void recordLatency(long aStartNanos) {
        if (sendLatencyNanos != null) {
            sendLatencyNanos.accept(System.nanoTime() - aStartNanos);
        }
    }

    private static void sleep(long aMs) {
        if (aMs <= 0) {
            return;
        }
        try {
            Thread.sleep(aMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** First ~500 chars of the response body, stripped, for error logs; never includes request headers. */
    private static String snippet(String aBody) {
        if (aBody == null) {
            return "";
        }
        String trimmed = aBody.strip();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…(" + trimmed.length() + " chars)" : trimmed;
    }

    /** A rejection that a retry cannot fix (4xx) — dead-letter straight away. */
    private static final class PermanentFailureException extends RuntimeException {
        PermanentFailureException(String aMessage) {
            super(aMessage);
        }
    }

    private static final class DeployReviewResponse {
        String status;
    }
}
