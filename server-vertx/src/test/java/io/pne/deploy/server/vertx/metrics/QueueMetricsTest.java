package io.pne.deploy.server.vertx.metrics;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QueueMetricsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void exposesQueueMetricsInScrape() {
        PersistentSpool spool = new PersistentSpool(tmp.getRoot());
        String first = spool.append("{\"x\":1}");
        spool.append("{\"x\":2}");
        spool.remove(first); // 1 pending, sent=1

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        QueueMetrics.register(registry, "redmine", spool);

        assertEquals(1.0, registry.get("deploy_queue_pending").tag("queue", "redmine").gauge().value(), 0.0);
        assertEquals(1.0, registry.get("deploy_queue_sent_total").tag("queue", "redmine").functionCounter().count(), 0.0);

        String scrape = registry.scrape();
        assertTrue(scrape.contains("deploy_queue_pending"));
        assertTrue(scrape.contains("deploy_queue_sent_total"));
    }

    @Test
    public void exposesSendLatencyHistogram() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        LongConsumer recorder = QueueMetrics.sendLatencyRecorder(registry, "telegram");

        recorder.accept(5_000_000L); // 5 ms

        assertEquals(1L, registry.get("deploy_queue_send_latency").tag("queue", "telegram").timer().count());
        assertTrue(registry.scrape().contains("deploy_queue_send_latency_seconds_bucket"));
    }

    @Test
    public void sendLatencyMaxAndPercentilesDoNotDecayToZero() {
        MockClock clock = new MockClock();
        SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        LongConsumer recorder = QueueMetrics.sendLatencyRecorder(registry, "telegram");

        recorder.accept(120_000_000L); // 120 ms

        // Advance far beyond the default 2-minute distribution-statistic window; with the fix the
        // window does not rotate, so max/percentiles keep the recorded value instead of decaying to 0.
        clock.add(Duration.ofMinutes(5));

        Timer timer = registry.find("deploy_queue_send_latency").tag("queue", "telegram").timer();
        HistogramSnapshot snap = timer.takeSnapshot();

        assertTrue("max must not decay to 0", snap.max(TimeUnit.MILLISECONDS) > 0.0);

        double p95 = 0.0;
        for (ValueAtPercentile v : snap.percentileValues()) {
            if (Math.abs(v.percentile() - 0.95) < 1e-6) {
                p95 = v.value(TimeUnit.MILLISECONDS);
            }
        }
        assertTrue("p95 must not decay to 0, was " + p95, p95 > 0.0);
    }
}
