package io.pne.deploy.server.vertx.metrics;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

/** Serves the Prometheus text exposition on {@code /metrics}. */
public class MetricsHttpHandler implements Handler<HttpServerRequest> {

    private final PrometheusMeterRegistry registry;

    public MetricsHttpHandler(PrometheusMeterRegistry aRegistry) {
        this.registry = aRegistry;
    }

    @Override
    public void handle(HttpServerRequest aRequest) {
        aRequest.response()
                .putHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                .end(registry.scrape());
    }
}
