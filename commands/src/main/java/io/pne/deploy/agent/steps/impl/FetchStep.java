package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.commands.StreamDeadline;
import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.PathGuard;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import io.pne.deploy.agent.steps.policy.UrlGuard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Downloads an artifact to a file.
 *
 * <p>Only the expected status counts as success. This is stricter than a plain download, which happily stores an
 * error page under the name of the artifact and lets the next step install it; here an unexpected status stops the
 * plan and nothing is written. Redirects are not followed, so a download cannot be steered to another host, and
 * the timeout covers the whole transfer rather than only the response headers - a source that answers and then
 * stops sending must not hold the plan open for ever.
 */
public class FetchStep implements IStep {

    public static final String TYPE = "fetch";

    private final String  url;
    private final String  to;
    private final int     expectStatus;
    private final boolean mkdirs;
    private final boolean atomic;
    private final int     timeoutSeconds;

    public FetchStep(StepParams aParams) throws StepValidationException {
        url            = aParams.required("url");
        to             = aParams.required("to");
        expectStatus   = aParams.intValue("expectStatus", 200);
        mkdirs         = aParams.boolValue("mkdirs", true);
        atomic         = aParams.boolValue("atomic", true);
        timeoutSeconds = aParams.intValue("timeoutSeconds", 300);
        if (timeoutSeconds <= 0) {
            throw aParams.error("timeoutSeconds", "must be positive, got " + timeoutSeconds);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "url", url);
        aScope.checkReferences(TYPE, "to", to);
        UrlGuard.check(aPolicy.getFetchHosts(), TYPE, "url", StepPlanScope.withPlaceholders(url, "0"));
        PathGuard.checkWritable(aPolicy, TYPE, "to", StepPlanScope.withPlaceholders(to, "0"));
        if (timeoutSeconds > aPolicy.getMaxStepSeconds()) {
            throw new StepValidationException("step '" + TYPE + "': timeoutSeconds " + timeoutSeconds
                    + " is over the limit of " + aPolicy.getMaxStepSeconds() + "s");
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        StepPolicy policy = aContext.getPolicy();
        try {
            URI  uri    = UrlGuard.check(policy.getFetchHosts(), TYPE, "url", aContext.expand(url));
            Path target = PathGuard.checkWritable(policy, TYPE, "to", aContext.expand(to));

            if (mkdirs) {
                FileOperations.createDirectories(target.getParent());
            }

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();

            aContext.log("GET " + uri);
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

            HttpResponse<InputStream> response = aContext.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            // The stream is closed from another thread once the deadline passes, which is the only thing that
            // unblocks a read waiting on a source that has stopped sending.
            try (StreamDeadline ignored = StreamDeadline.closeAt(response.body(), deadline)) {
                if (response.statusCode() != expectStatus) {
                    throw new StepExecutionException("GET " + uri + " answered " + response.statusCode()
                            + " but " + expectStatus + " was expected: " + firstBytes(response.body()));
                }

                long size = store(response.body(), target, policy.getMaxFetchBytes(), deadline);
                aContext.log("stored " + size + " byte(s) into " + target);
            }

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepExecutionException("Interrupted while downloading " + url, e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot download " + url + ": " + e, e);
        }
    }

    @Override
    public long getMaxSeconds() {
        return timeoutSeconds;
    }

    private long store(InputStream aBody, Path aTarget, long aMaxBytes, long aDeadline) throws IOException {
        if (!atomic) {
            try (InputStream in = aBody; OutputStream out = Files.newOutputStream(aTarget)) {
                return FileOperations.copyLimited(in, out, aMaxBytes, aDeadline);
            }
        }
        Path temp = FileOperations.temporarySibling(aTarget);
        try {
            long size;
            try (InputStream in = aBody; OutputStream out = Files.newOutputStream(temp)) {
                size = FileOperations.copyLimited(in, out, aMaxBytes, aDeadline);
            }
            FileOperations.moveInto(temp, aTarget);
            return size;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** A short excerpt of an unexpected answer, to make the failure readable without reading a whole error page. */
    private static String firstBytes(InputStream aBody) {
        try (InputStream in = aBody) {
            byte[] head = in.readNBytes(512);
            return new String(head, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        } catch (IOException e) {
            return "<no body>";
        }
    }
}
