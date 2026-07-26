package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.data_model.DiffCommit;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import io.pne.deploy.server.api.IAgentVersionReader;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskDiff;
import io.pne.deploy.server.api.task.TaskId;
import io.pne.deploy.server.api.task.TaskParameters;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end (within the class) test of {@link DiffServiceImpl#processDiff}: the GitLab / Telegram / Redmine
 * collaborators are injected as mocks via the additive constructor, so we can assert exactly what the feature
 * posts back to the user (the Redmine comment and the Telegram messages) without any network.
 */
public class DiffServiceProcessDiffTest {

    private final IRemoteRedmineService  redmine  = mock(IRemoteRedmineService.class);
    private final IRemoteGitlabService   gitlab   = mock(IRemoteGitlabService.class);
    private final IRemoteTelegramService telegram = mock(IRemoteTelegramService.class);
    private final IAgentVersionReader    reader   = mock(IAgentVersionReader.class);

    private final DiffServiceImpl diffService =
            new DiffServiceImpl(redmine, gitlab, telegram, "https://redmine.example", reader);

    @Test
    public void processDiffPostsAggregatedCommentAndTelegramMessages() {
        RedmineIssue issue = mock(RedmineIssue.class);
        when(issue.subject()).thenReturn("Fix the bug");
        when(redmine.getIssue(119126L)).thenReturn(issue);
        when(gitlab.getTagDiff(any(DiffTask.class)))
                .thenReturn(Arrays.asList(
                        new DiffCommit("chore: cleanup", LocalDate.of(2026, 7, 18)),
                        new DiffCommit("#119126 fix the bug", LocalDate.of(2026, 7, 20))));

        DiffTask task = new DiffTask(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        diffService.processDiff(singletonList(task), 42);

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(redmine).enqueueAddComment(eq(42), comment.capture());
        String redmineComment = comment.getValue();
        assertTrue("redmine comment: " + redmineComment,
                redmineComment.contains("|_. Date |_. Issue |_. Subject |"));
        assertTrue(redmineComment, redmineComment.contains("| 2026-07-20 | #119126 | Fix the bug |"));
        assertTrue(redmineComment, redmineComment.contains("| 2026-07-18 |  | chore: cleanup |"));
        // Newest first: the 2026-07-20 row precedes the 2026-07-18 row.
        assertTrue("expected desc date order in: " + redmineComment,
                redmineComment.indexOf("2026-07-20") < redmineComment.indexOf("2026-07-18"));

        ArgumentCaptor<List> telegramMessages = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendMessages(telegramMessages.capture());
        String joined = telegramMessages.getValue().toString();
        // The issue line prefixes the date, then wraps "#id - subject" in an <a> tag.
        assertTrue("telegram messages: " + joined, joined.contains("2026-07-20 — <a href="));
        assertTrue(joined, joined.contains("#119126 - Fix the bug"));
        assertTrue(joined, joined.contains("2026-07-18 — No Issue - chore: cleanup"));
    }

    @Test
    public void processDiffWithNoTasksTouchesNoCollaborators() {
        diffService.processDiff(Collections.emptyList(), 1);
        verifyNoInteractions(gitlab, telegram, redmine);
    }

    /**
     * The diff is driven by {@code task.diff} (versionUrl / gitlabProjectId / agent / newVersionArg), not by command
     * arguments; newVersion is the task-line argument at index {@code diff.newVersionArg} (1 = the first argument).
     * The old version is fetched through the agent (the deploy-server can't reach the firewalled versionUrl), so the
     * reader is mocked here.
     */
    @Test
    public void getCurrentVersionUsesTaskDiff() {
        String versionUrl = "http://firewalled.example/version";
        when(reader.readVersion("agent-1", versionUrl)).thenReturn("3.36.16-42");

        TaskDiff diff = TaskDiff.builder()
                .enabled(true).versionUrl(versionUrl).gitlabProjectId(42).agent("agent-1").newVersionArg(1).build();
        Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo 3.36.16-100", 168059, diff);

        List<DiffTask> diffTasks = diffService.getCurrentVersion(task);

        assertEquals(1, diffTasks.size());
        DiffTask built = diffTasks.get(0);
        assertEquals(Integer.valueOf(42), built.getGitlabProject());
        assertEquals("3.36.16-42", built.getOldVersion());
        assertEquals("3.36.16-100", built.getNewVersion());
        assertTrue("agent from diff is used, got: " + built.getIdsString(), built.getIdsString().contains("agent-1"));
    }

    @Test
    public void getCurrentVersionWithoutEnabledDiffReturnsEmpty() {
        Task noDiff = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo 1.2.3", 1, null);
        assertTrue(diffService.getCurrentVersion(noDiff).isEmpty());

        Task disabled = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo 1.2.3", 1,
                TaskDiff.builder().enabled(false).versionUrl("http://x").gitlabProjectId(1).build());
        assertTrue(diffService.getCurrentVersion(disabled).isEmpty());
    }

    @Test
    public void getCurrentVersionPicksTheConfiguredArgumentIndex() {
        String versionUrl = "http://firewalled.example/version";
        when(reader.readVersion("agent-1", versionUrl)).thenReturn("3.36.16-42");

        TaskDiff diff = TaskDiff.builder()
                .enabled(true).versionUrl(versionUrl).gitlabProjectId(42).agent("agent-1").newVersionArg(2).build();
        Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo skip 3.36.16-100", 168059, diff);

        List<DiffTask> diffTasks = diffService.getCurrentVersion(task);

        assertEquals(1, diffTasks.size());
        assertEquals("3.36.16-100", diffTasks.get(0).getNewVersion()); // 2nd argument, not the 1st ("skip")
    }

    @Test
    public void getCurrentVersionWithoutNewVersionArgIsSkipped() {
        TaskDiff diff = TaskDiff.builder() // newVersionArg defaults to 0
                .enabled(true).versionUrl("http://x").gitlabProjectId(42).agent("agent-1").build();
        Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo 3.36.16-100", 1, diff);

        assertTrue(diffService.getCurrentVersion(task).isEmpty());
        verifyNoInteractions(reader);
    }

    @Test
    public void getCurrentVersionWithArgIndexOutOfRangeIsSkipped() {
        TaskDiff diff = TaskDiff.builder()
                .enabled(true).versionUrl("http://x").gitlabProjectId(42).agent("agent-1").newVersionArg(5).build();
        Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "demo 3.36.16-100", 1, diff); // only tokens[0..1]

        assertTrue(diffService.getCurrentVersion(task).isEmpty());
        verifyNoInteractions(reader);
    }
}
