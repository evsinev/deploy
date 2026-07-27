package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.data_model.DiffKey;
import io.pne.deploy.client.redmine.process.data_model.DiffLink;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.data_model.DiffCommit;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import io.pne.deploy.server.api.IAgentVersionReader;
import org.junit.Test;

import java.time.LocalDate;
import java.util.*;

import static com.payneteasy.startup.parameters.StartupParametersFactory.getStartupParameters;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DiffServiceImplTest {

    private final IRemoteRedmineService redmine = mock(IRemoteRedmineService.class);
    private final DiffServiceImpl diffService =
            new DiffServiceImpl(redmine, getStartupParameters(IRedmineRemoteConfig.class), mock(IAgentVersionReader.class));

    // --- aggregate ---

    @Test
    public void aggregateMergesSameKeyAndUnionsIds() {
        List<DiffTask> tasks = Arrays.asList(
                task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0"),
                task(new String[]{"host-2"}, 1, "svc", "1.0.0", "1.1.0"));

        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);

        assertEquals(1, aggregated.size());
        DiffTask merged = aggregated.values().iterator().next();
        assertEquals(new HashSet<>(Arrays.asList("host-1", "host-2")), merged.getIds());
    }

    @Test
    public void aggregateKeepsTasksWithDifferentVersions() {
        List<DiffTask> tasks = Arrays.asList(
                task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0"),
                task(new String[]{"host-2"}, 1, "svc", "1.0.0", "1.2.0"));

        assertEquals(2, diffService.aggregate(tasks).size());
    }

    @Test
    public void aggregateNullIsEmpty() {
        assertTrue(diffService.aggregate(null).isEmpty());
    }

    // --- mapDiffIssues ---

    @Test
    public void mapDiffIssuesResolvesSubjectForIssueRef() {
        RedmineIssue issue = mock(RedmineIssue.class);
        when(issue.subject()).thenReturn("Fix the thing");
        when(redmine.getIssue(119126L)).thenReturn(issue);

        List<DiffLink> links = diffService.mapDiffIssues(
                Arrays.asList(
                        new DiffCommit("#119126 did stuff", LocalDate.of(2026, 7, 20)),
                        new DiffCommit("no issue here", null)),
                new HashMap<>());

        assertEquals(2, links.size());

        DiffLink withIssue = links.get(0);
        assertEquals(Integer.valueOf(119126), withIssue.getRedmineIssueId());
        assertEquals("Fix the thing", withIssue.getRedmineIssueSubject());
        assertTrue(withIssue.getRedmineUrl().endsWith("/issues/119126"));
        assertEquals(LocalDate.of(2026, 7, 20), withIssue.getCommitDate());

        DiffLink noIssue = links.get(1);
        assertNull(noIssue.getRedmineIssueId());
        assertNull(noIssue.getRedmineIssueSubject());
    }

    @Test
    public void mapDiffIssuesNullDiffsIsEmptyAndTouchesNoRedmine() {
        assertTrue(diffService.mapDiffIssues(null, new HashMap<>()).isEmpty());
        verifyNoInteractions(redmine);
    }

    // --- constructRedmineMessage ---

    @Test
    public void constructRedmineMessageDedupsIssueAndRendersHeader() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");

        String msg = diffService.constructRedmineMessage(t, Arrays.asList(
                issueLink(119126, "Subj A"),
                issueLink(119126, "Subj A"),          // same id -> rendered once
                noIssueLink("chore: bump deps")));

        assertTrue(msg.contains("(1.0.0 → 1.1.0)"));
        assertTrue(msg.contains("|_. Issue |_. Subject |"));
        assertTrue(msg, msg.contains("|\\2. *(no date)* |")); // undated commits grouped under one header
        assertEquals(1, countOccurrences(msg, "| #119126 |"));
        assertTrue(msg.contains("Subj A"));
        assertTrue(msg.contains("| chore: bump deps |"));
    }

    // --- grouping by date ---

    @Test
    public void constructRedmineMessageGroupsCommitsByDate() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");

        String msg = diffService.constructRedmineMessage(t, Arrays.asList(
                dated(20260720, "first on the 20th"),
                dated(20260720, "second on the 20th"),
                dated(20260719, "on the 19th")));

        // One colspan header per date (the same date is not repeated), newest first.
        assertEquals(1, countOccurrences(msg, "|\\2. *2026-07-20* |"));
        assertEquals(1, countOccurrences(msg, "|\\2. *2026-07-19* |"));
        assertTrue(msg, msg.indexOf("2026-07-20") < msg.indexOf("2026-07-19"));
        assertTrue(msg.contains("| first on the 20th |"));
        assertTrue(msg.contains("| second on the 20th |"));
    }

    @Test
    public void constructTelegramMessageGroupsCommitsByDate() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");

        List<String> chunks = diffService.constructTelegramMessage(t, Arrays.asList(
                dated(20260720, "first on the 20th"),
                dated(20260720, "second on the 20th"),
                dated(20260719, "on the 19th")));

        assertEquals(1, chunks.size());
        String msg = chunks.get(0);
        // The date appears once as a header line, not on each bullet.
        assertEquals(1, countOccurrences(msg, "2026-07-20\n"));
        assertEquals(1, countOccurrences(msg, "2026-07-19\n"));
        assertTrue(msg, msg.indexOf("2026-07-20") < msg.indexOf("2026-07-19"));
        assertTrue(msg.contains("• No Issue - first on the 20th"));
        assertTrue(msg.contains("• No Issue - second on the 20th"));
    }

    // --- constructTelegramMessage (normal path) ---

    @Test
    public void constructTelegramMessageBuildsSingleChunkWithLink() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        DiffLink l = issueLink(119126, "Fix");
        l.setRedmineUrl("https://redmine.example/issues/119126");

        List<String> chunks = diffService.constructTelegramMessage(t, Collections.singletonList(l));

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("(no date)")); // undated commit still grouped under a header
        assertTrue(chunks.get(0).contains("<a href=\"https://redmine.example/issues/119126\">"));
        assertTrue(chunks.get(0).contains("#119126 - Fix"));
    }

    // --- DiffTask helpers ---

    @Test
    public void diffTaskIdsStringJoinsAndFiltersNull() {
        DiffTask t = task(new String[]{"a", null, "b"}, 1, "svc", "1", "2");
        String ids = t.getIdsString();
        assertTrue(ids.contains("a"));
        assertTrue(ids.contains("b"));
        assertFalse(ids.contains("null"));
    }

    @Test
    public void diffTaskAddIdsUnionsSets() {
        DiffTask t = task(new String[]{"a"}, 1, "svc", "1", "2");
        t.addIds(new HashSet<>(Arrays.asList("b", "c")));
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), t.getIds());
    }

    // --- parseRedmineIssueIdFromCommitMessage (regex fix) ---

    @Test
    public void parsesFullSevenDigitIssueId() {
        assertEquals(Integer.valueOf(1234567),
                diffService.parseRedmineIssueIdFromCommitMessage("#1234567 fix"));
    }

    @Test
    public void parsesThreeDigitIssueId() {
        assertEquals(Integer.valueOf(123),
                diffService.parseRedmineIssueIdFromCommitMessage("fix #123"));
    }

    @Test
    public void parsesIssueIdEmbeddedInText() {
        assertEquals(Integer.valueOf(12345),
                diffService.parseRedmineIssueIdFromCommitMessage("abc#12345def"));
    }

    @Test
    public void returnsNullWhenNoIssueRef() {
        assertNull(diffService.parseRedmineIssueIdFromCommitMessage("no issue here"));
    }

    // --- constructTelegramMessage (splitting robustness) ---

    @Test
    public void telegramLongHeaderAndLongLineDoesNotThrow() {
        DiffTask t = task(new String[]{"host-1"}, 1, "x".repeat(4100), "1.0.0", "1.1.0");
        List<String> chunks = diffService.constructTelegramMessage(
                t, Collections.singletonList(noIssueLink("y".repeat(5000))));
        assertFalse(chunks.isEmpty());
    }

    @Test
    public void telegramOversizedLineStaysWithinLimitForNormalHeader() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        List<String> chunks = diffService.constructTelegramMessage(
                t, Collections.singletonList(noIssueLink("y".repeat(5000))));
        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue("chunk length " + chunk.length() + " exceeds limit", chunk.length() <= 4000);
        }
    }

    // --- helpers ---

    private static DiffTask task(String[] ids, int project, String name, String oldV, String newV) {
        return new DiffTask(ids, project, name, oldV, newV);
    }

    private static DiffLink issueLink(Integer issueId, String subject) {
        DiffLink l = new DiffLink();
        l.setRedmineIssueId(issueId);
        l.setRedmineIssueSubject(subject);
        return l;
    }

    private static DiffLink noIssueLink(String commitMessage) {
        DiffLink l = new DiffLink();
        l.setCommitMessage(commitMessage);
        return l;
    }

    /** A no-issue commit dated with a compact {@code yyyyMMdd} literal, e.g. {@code 20260720}. */
    private static DiffLink dated(int yyyymmdd, String commitMessage) {
        DiffLink l = noIssueLink(commitMessage);
        l.setCommitDate(LocalDate.of(yyyymmdd / 10000, (yyyymmdd / 100) % 100, yyyymmdd % 100));
        return l;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i != -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
