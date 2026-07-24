package io.pne.deploy.server.vertx.metrics;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
}
