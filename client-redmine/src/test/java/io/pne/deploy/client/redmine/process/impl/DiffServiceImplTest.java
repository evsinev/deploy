package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.data_model.DiffKey;
import io.pne.deploy.client.redmine.process.data_model.DiffLink;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.junit.Test;

import java.util.*;

import static com.payneteasy.startup.parameters.StartupParametersFactory.getStartupParameters;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DiffServiceImplTest {

    private final IRemoteRedmineService redmine = mock(IRemoteRedmineService.class);
    private final DiffServiceImpl diffService =
            new DiffServiceImpl(redmine, getStartupParameters(IRedmineRemoteConfig.class));

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
                Arrays.asList("#119126 did stuff", "no issue here"), new HashMap<>());

        assertEquals(2, links.size());

        DiffLink withIssue = links.get(0);
        assertEquals(Integer.valueOf(119126), withIssue.getRedmineIssueId());
        assertEquals("Fix the thing", withIssue.getRedmineIssueSubject());
        assertTrue(withIssue.getRedmineUrl().endsWith("/issues/119126"));

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
        assertEquals(1, countOccurrences(msg, "#119126 - "));
        assertTrue(msg.contains("No Issue - chore: bump deps"));
    }

    // --- constructTelegramMessage (normal path) ---

    @Test
    public void constructTelegramMessageBuildsSingleChunkWithLink() {
        DiffTask t = task(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        DiffLink l = issueLink(119126, "Fix");
        l.setRedmineUrl("https://redmine.example/issues/119126");

        List<String> chunks = diffService.constructTelegramMessage(t, Collections.singletonList(l));

        assertEquals(1, chunks.size());
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

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i != -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
