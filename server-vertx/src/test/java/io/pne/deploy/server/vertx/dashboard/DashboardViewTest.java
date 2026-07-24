package io.pne.deploy.server.vertx.dashboard;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.vertx.status.model.TaskState;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DashboardViewTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void agentsListsIdsAndCount() {
        Set<String> agents = new LinkedHashSet<>(Arrays.asList("host-a", "host-b"));
        String html = DashboardView.agents(agents);
        assertTrue(html, html.contains("host-a"));
        assertTrue(html, html.contains("host-b"));
        assertTrue(html, html.contains(">2<")); // count
    }

    @Test
    public void agentsEmptyShowsPlaceholder() {
        String html = DashboardView.agents(emptySet());
        assertTrue(html, html.contains("no agents connected"));
    }

    @Test
    public void issuesShowsSizeAndIds() {
        String html = DashboardView.issues(Arrays.asList(11L, 22L, 33L));
        assertTrue(html, html.contains(">3<")); // pending count
        assertTrue(html, html.contains("#22"));
    }

    @Test
    public void queuesRendersSpoolCounters() throws Exception {
        PersistentSpool spool = new PersistentSpool(tmp.newFolder("telegram"));
        String first = spool.append("{\"x\":1}");
        spool.append("{\"x\":2}");
        spool.remove(first); // 1 pending, 1 sent

        Map<String, PersistentSpool> queues = new LinkedHashMap<>();
        queues.put("telegram", spool);

        String html = DashboardView.queues(queues);
        assertTrue(html, html.contains("telegram"));
        assertTrue(html, html.contains("<td class=\"num\">1</td>")); // pending and sent are both 1
    }

    @Test
    public void queuesEmptyShowsPlaceholder() {
        assertTrue(DashboardView.queues(new LinkedHashMap<>()).contains("no queues"));
    }

    @Test
    public void statusNullIsIdleWithoutException() {
        String html = DashboardView.status(null);
        assertTrue(html, html.contains("idle"));
    }

    @Test
    public void statusRendersTaskFields() {
        TaskStatus status = TaskStatus.builder()
                .taskId("task-1")
                .issueId(42)
                .taskLine("> deploy web")
                .taskState(TaskState.taskRunning())
                .build();
        String html = DashboardView.status(status);
        assertTrue(html, html.contains("task-1"));
        assertTrue(html, html.contains("42"));
        assertTrue(html, html.contains("RUNNING"));
    }

    @Test
    public void escEscapesMarkup() {
        assertEquals("&lt;x&gt;", DashboardView.esc("<x>"));
        assertEquals("a &amp; b", DashboardView.esc("a & b"));
    }

    @Test
    public void agentIdWithMarkupIsEscaped() {
        String html = DashboardView.agents(new LinkedHashSet<>(Arrays.asList("<script>")));
        assertTrue(html, html.contains("&lt;script&gt;"));
        assertFalse(html, html.contains("<script>"));
    }

    @Test
    public void serviceRendersHumanReadableVitals() {
        // 1h 1m 5s uptime, 5 MiB / 10 MiB heap, 3 agents
        String html = DashboardView.service(3_665_000L, 5L * 1024 * 1024, 10L * 1024 * 1024, 3);
        assertTrue(html, html.contains("1h"));
        assertTrue(html, html.contains("MiB"));
        assertTrue(html, html.contains(">3<"));
    }

    @Test
    public void formatBytesAndDurationHelpers() {
        assertEquals("512 B", DashboardView.formatBytes(512));
        assertEquals("1.0 KiB", DashboardView.formatBytes(1024));
        assertTrue(DashboardView.formatDuration(0).contains("0s"));
    }
}
