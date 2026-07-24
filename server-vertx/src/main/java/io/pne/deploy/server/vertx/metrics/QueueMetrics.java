package io.pne.deploy.server.vertx.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;

/** Registers Prometheus metrics for a durable queue's spool: pending/dead gauges + sent/dead-lettered counters. */
public final class QueueMetrics {

    private QueueMetrics() {
    }

    public static void register(MeterRegistry aRegistry, String aQueue, PersistentSpool aSpool) {
        Gauge.builder("deploy_queue_pending", aSpool, PersistentSpool::size)
                .description("Pending (not yet sent) operations spooled")
                .tag("queue", aQueue)
                .register(aRegistry);

        Gauge.builder("deploy_queue_dead", aSpool, PersistentSpool::deadSize)
                .description("Operations currently in the dead-letter directory")
                .tag("queue", aQueue)
                .register(aRegistry);

        FunctionCounter.builder("deploy_queue_sent_total", aSpool, PersistentSpool::sentCount)
                .description("Operations successfully sent since start")
                .tag("queue", aQueue)
                .register(aRegistry);

        FunctionCounter.builder("deploy_queue_deadlettered_total", aSpool, PersistentSpool::deadLetterCount)
                .description("Operations dead-lettered since start")
                .tag("queue", aQueue)
                .register(aRegistry);
    }
}
