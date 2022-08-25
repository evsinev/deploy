package io.pne.deploy.server.vertx.http;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class StatusHttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = getLogger(StatusHttpHandler.class);

    @Override
    public void handle(HttpServerRequest event) {

    }
}
