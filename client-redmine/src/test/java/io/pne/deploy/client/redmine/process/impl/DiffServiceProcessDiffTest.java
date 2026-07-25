package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.server.api.task.AgentFinder;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.TaskId;
import io.pne.deploy.server.api.task.TaskParameters;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
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

    private final DiffServiceImpl diffService =
            new DiffServiceImpl(redmine, gitlab, telegram, "https://redmine.example");

    @Test
    public void processDiffPostsAggregatedCommentAndTelegramMessages() {
        RedmineIssue issue = mock(RedmineIssue.class);
        when(issue.subject()).thenReturn("Fix the bug");
        when(redmine.getIssue(119126L)).thenReturn(issue);
        when(gitlab.getTagDiff(any(DiffTask.class)))
                .thenReturn(Arrays.asList("#119126 fix the bug", "chore: cleanup"));

        DiffTask task = new DiffTask(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        diffService.processDiff(singletonList(task), 42);

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(redmine).enqueueAddComment(eq(42), comment.capture());
        assertTrue("redmine comment: " + comment.getValue(),
                comment.getValue().contains("#119126 - Fix the bug"));
        assertTrue(comment.getValue().contains("No Issue - chore: cleanup"));

        ArgumentCaptor<List> telegramMessages = ArgumentCaptor.forClass(List.class);
        verify(telegram).sendMessages(telegramMessages.capture());
        String joined = telegramMessages.getValue().toString();
        assertTrue("telegram messages: " + joined, joined.contains("#119126 - Fix the bug"));
        assertTrue(joined.contains("No Issue - chore: cleanup"));
    }

    @Test
    public void processDiffWithNoTasksTouchesNoCollaborators() {
        diffService.processDiff(Collections.emptyList(), 1);
        verifyNoInteractions(gitlab, telegram, redmine);
    }

    /**
     * Diff-eligibility is decided purely by the arguments (version + old-version url + gitlab=&lt;id&gt;), not by
     * the script name: a "sandbox" command with a gitlab token is now included, and one without a token is not.
     */
    @Test
    public void getCurrentVersionSelectsByGitlabTokenNotName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/version", exchange -> {
            byte[] body = "3.36.16-42\n".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String oldVersionUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/version";
        try {
            // name contains "sandbox" but has version + url + gitlab=114 -> must be INCLUDED
            TaskCommand ui = new TaskCommand(
                    new AgentFinder(new String[]{"dui-1"}),
                    new AgentCommand(new AgentCommandParameters(), AgentCommandType.SHELL,
                            "./bin/redeploy-sandbox-ui.sh",
                            Arrays.asList("3.36.16-100", oldVersionUrl, "gitlab=114")));
            // no url, no gitlab token -> must be EXCLUDED
            TaskCommand skin = new TaskCommand(
                    new AgentFinder(new String[]{"dpub-2"}),
                    new AgentCommand(new AgentCommandParameters(), AgentCommandType.SHELL,
                            "./bin/redeploy-sandbox-skin.sh",
                            singletonList("3.36.16-100")));
            Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                    Arrays.asList(ui, skin), "msk-sandbox-ui 3.36.16-100", 168059);

            List<DiffTask> diffTasks = diffService.getCurrentVersion(task);

            assertEquals("only the command carrying gitlab=<id> is eligible", 1, diffTasks.size());
            DiffTask built = diffTasks.get(0);
            assertEquals(Integer.valueOf(114), built.getGitlabProject());
            assertEquals("3.36.16-42", built.getOldVersion());
            assertEquals("3.36.16-100", built.getNewVersion());
        } finally {
            server.stop(0);
        }
    }
}
