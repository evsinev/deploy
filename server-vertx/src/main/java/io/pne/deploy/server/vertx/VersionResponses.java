package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.GetVersionResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Correlates a {@link GetVersionResponse} coming back from an agent to the request that is waiting for it. */
public class VersionResponses {

    private static final int WAIT_TIMEOUT_SECONDS = 60;

    private final Map<String, GetVersionResponse> map = new ConcurrentHashMap<>();

    public GetVersionResponse awaitForVersionResponse(String aRequestId) throws InterruptedException {
        for (int i = 0; i < WAIT_TIMEOUT_SECONDS; i++) {
            GetVersionResponse response = map.remove(aRequestId);
            if (response != null) {
                return response;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("No version response for " + aRequestId + " within " + WAIT_TIMEOUT_SECONDS + "s");
    }

    public void addResponse(String aRequestId, GetVersionResponse aResponse) {
        map.put(aRequestId, aResponse);
    }
}
