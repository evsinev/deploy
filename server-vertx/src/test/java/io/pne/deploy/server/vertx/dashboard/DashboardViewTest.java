package io.pne.deploy.server.vertx.dashboard;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.vertx.AgentRegistry;
import io.pne.deploy.server.service.impl.alias.AliasCommand;
import io.pne.deploy.server.service.impl.alias.AliasDescription;
import io.pne.deploy.server.service.impl.alias.AliasDiff;
import io.pne.deploy.server.vertx.status.model.TaskState;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
        assertTrue(html, html.contains("2 connected")); // count
    }

    @Test
    public void agentsAreSortedAlphabetically() {
        String html = DashboardView.agents(new LinkedHashSet<>(List.of("dvpn-2", "ipro-1", "dpub-1")));
        int dpub = html.indexOf("dpub-1");
        int dvpn = html.indexOf("dvpn-2");
        int ipro = html.indexOf("ipro-1");
        assertTrue(dpub >= 0 && dvpn >= 0 && ipro >= 0);
        assertTrue("dpub-1 before dvpn-2", dpub < dvpn);
        assertTrue("dvpn-2 before ipro-1", dvpn < ipro);
    }

    @Test
    public void agentsEmptyShowsPlaceholder() {
        String html = DashboardView.agents(emptySet());
        assertTrue(html, html.contains("no agents connected"));
    }

    @Test
    public void agentDetailsRendersAllFieldsForConnectedAgent() {
        long connectedAt = 1_000L;
        long now = connectedAt + 65_000L; // 1m 5s uptime
        AgentRegistry.AgentRecord record = new AgentRegistry.AgentRecord(
                "host-a", "10.0.0.5", connectedAt, now, AgentRegistry.Status.CONNECTED,
                "1.0-15", 5L * 1024 * 1024, 10L * 1024 * 1024);
        String html = DashboardView.agentDetails(List.of(record), now);
        assertTrue(html, html.contains("host-a"));
        assertTrue(html, html.contains("10.0.0.5"));
        assertTrue(html, html.contains("1m 5s"));
        assertTrue(html, html.contains("5.0 MiB / 10.0 MiB"));
        assertTrue(html, html.contains("1.0-15"));
        assertTrue(html, html.contains("badge ok"));
        assertTrue(html, html.contains("connected"));
    }

    @Test
    public void agentDetailsEmptyCellsAndDisconnectedBadgeForMissingData() {
        AgentRegistry.AgentRecord record = new AgentRegistry.AgentRecord(
                "host-b", null, 0, 5_000L, AgentRegistry.Status.DISCONNECTED, null, null, null);
        String html = DashboardView.agentDetails(List.of(record), 10_000L);
        assertTrue(html, html.contains("host-b"));
        assertTrue(html, html.contains("&mdash;"));      // ip / version / heap / uptime all missing
        assertTrue(html, html.contains("badge bad"));
        assertTrue(html, html.contains("disconnected"));
    }

    @Test
    public void agentDetailsEscapesFields() {
        AgentRegistry.AgentRecord record = new AgentRegistry.AgentRecord(
                "<x>", "<ip>", 0, 0, AgentRegistry.Status.CONNECTED, "<v>", 1L, 2L);
        String html = DashboardView.agentDetails(List.of(record), 0L);
        assertTrue(html, html.contains("&lt;x&gt;"));
        assertFalse(html, html.contains("<x>"));
    }

    @Test
    public void agentDetailsEmptyShowsPlaceholder() {
        assertTrue(DashboardView.agentDetails(new ArrayList<>(), 0L).contains("no agents"));
    }

    @Test
    public void issuesShowsSizeAndIds() {
        String html = DashboardView.issues(Arrays.asList(11L, 22L, 33L));
        assertTrue(html, html.contains(">3<")); // pending count
        assertTrue(html, html.contains("#22"));
    }

    @Test
    public void deliveryRendersCountersAndLatencyColumns() throws Exception {
        PersistentSpool spool = new PersistentSpool(tmp.newFolder("telegram"));
        String first = spool.append("{\"x\":1}");
        spool.append("{\"x\":2}");
        spool.remove(first); // 1 pending, 1 sent

        Map<String, PersistentSpool> queues = new LinkedHashMap<>();
        queues.put("telegram", spool);
        Map<String, LatencyStat> latency = new LinkedHashMap<>();
        latency.put("telegram", new LatencyStat(16, 126.0, 98.0, 210.0, 244.0, 251.0));

        String html = DashboardView.delivery(queues, latency);
        assertTrue(html, html.contains("<table class=\"delivery\""));
        assertTrue(html, html.contains("telegram"));
        assertTrue(html, html.contains(">16<"));   // n column
        assertTrue(html, html.contains("210 ms")); // p95 latency value
        assertTrue(html, html.contains("<th class=\"l\">queue</th>"));
    }

    @Test
    public void deliveryWithoutLatencyShowsDashes() throws Exception {
        PersistentSpool spool = new PersistentSpool(tmp.newFolder("redmine"));
        spool.append("{\"x\":1}");
        Map<String, PersistentSpool> queues = new LinkedHashMap<>();
        queues.put("redmine", spool);

        String html = DashboardView.delivery(queues, new LinkedHashMap<>());
        assertTrue(html, html.contains("redmine"));
        assertTrue(html, html.contains("&mdash;")); // no latency yet -> em dashes
    }

    @Test
    public void deliveryEmptyShowsPlaceholder() {
        assertTrue(DashboardView.delivery(new LinkedHashMap<>(), new LinkedHashMap<>()).contains("no queues"));
    }

    @Test
    public void statusNullIsIdleWithoutException() {
        String html = DashboardView.status(null, "");
        assertTrue(html, html.contains("idle"));
    }

    @Test
    public void statusRendersTaskFieldsAndIssueLink() {
        TaskStatus status = TaskStatus.builder()
                .taskId("task-1")
                .issueId(42)
                .taskLine("> deploy web")
                .taskState(TaskState.taskRunning())
                .build();
        String html = DashboardView.status(status, "https://redmine.example.com/");
        assertTrue(html, html.contains("task-1"));
        assertTrue(html, html.contains("RUNNING"));
        assertTrue(html, html.contains("badge"));
        assertTrue(html, html.contains("href=\"https://redmine.example.com/issues/42\"")); // trailing slash trimmed
        assertTrue(html, html.contains("#42"));
    }

    @Test
    public void statusWithoutRedmineUrlShowsPlainIssue() {
        TaskStatus status = TaskStatus.builder()
                .taskId("t").issueId(7).taskState(TaskState.taskRunning()).build();
        String html = DashboardView.status(status, "");
        assertFalse(html, html.contains("<a "));
        assertTrue(html, html.contains("#7"));
    }

    @Test
    public void statusRendersErrorMessage() {
        TaskStatus status = TaskStatus.builder()
                .taskId("t").issueId(9).taskState(TaskState.taskError("boom <fail>")).build();
        String html = DashboardView.status(status, "");
        assertTrue(html, html.contains("task-err"));
        assertTrue(html, html.contains("boom &lt;fail&gt;")); // escaped
    }

    @Test
    public void agentLogRowsRenderMessagesOldestFirstAndEscape() {
        AgentLogBuffer buffer = new AgentLogBuffer(10);
        buffer.add("dui-1", "cmd-123456789", "first line");
        buffer.add("dui-1", "cmd-123456789", "<script>bad</script>");

        String html = DashboardView.agentLogRows(buffer.tail(10));
        assertTrue(html, html.contains("first line"));
        assertTrue(html, html.contains("dui-1")); // source agent is shown
        assertTrue(html, html.contains("&lt;script&gt;bad&lt;/script&gt;"));
        assertFalse(html, html.contains("<script>bad"));
        assertTrue(html, html.contains("logline"));
        // oldest first: "first line" (added first) appears before the escaped script line (added last)
        assertTrue(html.indexOf("first line") < html.indexOf("bad"));
    }

    @Test
    public void agentLogRowsEmptyIsBlank() {
        assertEquals("", DashboardView.agentLogRows(new java.util.ArrayList<>()));
    }

    @Test
    public void configRendersGroupsMaskedSecretAndSetChip() {
        List<StartupConfigReport.Entry> entries = List.of(
                new StartupConfigReport.Entry("Redmine", "REDMINE_URL", "http://x", "", false, false),
                new StartupConfigReport.Entry("Redmine", "TELEGRAM_TOKEN", "•••• (set)", "", true, false));
        String html = DashboardView.config(entries);
        assertTrue(html, html.contains("cfg-toolbar"));   // filter toolbar
        assertTrue(html, html.contains("Redmine"));       // group card header
        assertTrue(html, html.contains("REDMINE_URL"));
        assertTrue(html, html.contains("••••"));          // secret masked
        assertTrue(html, html.contains("cfg-set"));       // "set" chip for the secret
        assertTrue(html, html.contains("v-str"));         // non-secret value colour class
    }

    @Test
    public void aliasSidebarRendersItemsWithHxGetAndCount() {
        String html = DashboardView.aliasSidebar(List.of(new DashboardView.AliasInfo("deploy-demo", 2)), "/deploy/dashboard");
        assertTrue(html, html.contains("deploy-demo"));
        assertTrue(html, html.contains("2 commands"));
        assertTrue(html, html.contains("hx-get=\"/deploy/dashboard/alias?name=deploy-demo\""));
    }

    @Test
    public void aliasSidebarSingleCommandIsSingular() {
        String html = DashboardView.aliasSidebar(List.of(new DashboardView.AliasInfo("solo", 1)), "/x");
        assertTrue(html, html.contains("1 command"));
        assertFalse(html, html.contains("1 commands"));
    }

    @Test
    public void aliasSidebarEmptyShowsPlaceholder() {
        assertTrue(DashboardView.aliasSidebar(new ArrayList<>(), "/x").contains("no aliases"));
    }

    @Test
    public void aliasDetailRendersCommandsAndEscapes() {
        AliasCommand command = new AliasCommand();
        command.agents = "agent-1,agent-2";
        command.name = "echo";
        command.arguments = List.of("<hi>", "$1");
        AliasDescription description = new AliasDescription();
        description.commands = List.of(command);

        String html = DashboardView.aliasDetail("deploy-demo", description, "commands: []");
        assertTrue(html, html.contains("deploy-demo"));
        assertTrue(html, html.contains("echo"));
        assertTrue(html, html.contains("agent-1"));
        assertTrue(html, html.contains("agent-2"));
        assertTrue(html, html.contains("&lt;hi&gt;"));
        assertFalse(html, html.contains("<hi>"));
        assertTrue(html, html.contains("show yaml"));
        assertTrue(html, html.contains("cmd-card"));
    }

    @Test
    public void aliasDetailRendersDiffCardWhenPresent() {
        AliasCommand command = new AliasCommand();
        command.agents = "dui-1";
        command.name = "./bin/redeploy.sh";
        command.arguments = List.of("$1");
        AliasDiff diff = new AliasDiff();
        diff.enabled = true;
        diff.versionUrl = "http://h/version.txt?filter=<v>";
        diff.gitlabProjectId = 114;
        diff.agent = "dui-1";
        diff.newVersionArg = 1;
        AliasDescription description = new AliasDescription();
        description.commands = List.of(command);
        description.diff = diff;

        String html = DashboardView.aliasDetail("msk-sandbox-ui", description, "diff: {}");
        assertTrue(html, html.contains(">diff<"));
        assertTrue(html, html.contains("version url"));
        assertTrue(html, html.contains("gitlab project"));
        assertTrue(html, html.contains("new version arg"));
        assertTrue(html, html.contains("114"));
        assertTrue(html, html.contains("true"));
        assertTrue(html, html.contains("filter=&lt;v&gt;"));
        assertFalse(html, html.contains("filter=<v>"));
    }

    @Test
    public void aliasDetailOmitsDiffCardWhenAbsent() {
        AliasCommand command = new AliasCommand();
        command.agents = "dui-1";
        command.name = "./bin/redeploy.sh";
        command.arguments = List.of("$1");
        AliasDescription description = new AliasDescription();
        description.commands = List.of(command);

        String html = DashboardView.aliasDetail("plain", description, "commands: []");
        assertFalse(html, html.contains("version url"));
    }

    @Test
    public void aliasDetailNullDescriptionIsSafe() {
        assertTrue(DashboardView.aliasDetail("x", null, null).contains("could not parse"));
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
