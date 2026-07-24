package io.pne.deploy.server.vertx.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;

import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

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

    /**
     * Creates a latency histogram for a queue and returns a recorder to feed it (nanoseconds per successful call).
     * The returned {@link LongConsumer} can be passed straight into the queue constructors.
     */
    public static LongConsumer sendLatencyRecorder(MeterRegistry aRegistry, String aQueue) {
        Timer timer = Timer.builder("deploy_queue_send_latency")
                .description("Latency of a successful send/edit call to the external service")
                .publishPercentileHistogram()
                .tag("queue", aQueue)
                .register(aRegistry);
        return nanos -> timer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
