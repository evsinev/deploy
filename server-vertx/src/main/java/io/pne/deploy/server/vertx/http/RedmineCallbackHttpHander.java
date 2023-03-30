package io.pne.deploy.server.vertx.http;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;

public class RedmineCallbackHttpHander implements Handler<HttpServerRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(RedmineCallbackHttpHander.class);

    private final Collection<Long> issues;

    public RedmineCallbackHttpHander(Collection<Long> issues) {
        this.issues = issues;
    }

    @Override
    public void handle(HttpServerRequest aEvent) {
        aEvent.bodyHandler(aBodyEvent -> {
            try {
                JsonObject json = aBodyEvent.toJsonObject();
                long issueId = json.getLong("issue_id");
                LOG.debug("Issue id is {}", issueId);
                aEvent.response().end("The callback is received\n");
                issues.add(issueId);
            } catch (Exception e) {
                String errorId = UUID.randomUUID().toString();
                LOG.error("Cannot process request: {}", errorId, e);
                aEvent.response().setStatusCode(500).end("Error id is " + errorId);
            }
        });
    }
}
