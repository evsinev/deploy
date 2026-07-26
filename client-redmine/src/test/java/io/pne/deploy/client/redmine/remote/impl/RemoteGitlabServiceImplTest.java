package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.http.client.api.HttpResponse;
import com.payneteasy.http.client.api.IHttpClient;
import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteGitlabServiceImplTest {

    private static final String GITLAB_URL = "https://code.clubber.me/git"; // includes the custom /git prefix

    @Ignore // manual: hits real GitLab
    @Test
    public void getDiffTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteGitlabService gitlabService = new RemoteGitlabServiceImpl(config);
        DiffTask diffTask = new DiffTask(new String[0], 1, "test", "1", "2");
        System.out.println(gitlabService.getTagDiff(diffTask).stream()
                .map(c -> c.getDate() + " " + c.getMessage())
                .collect(java.util.stream.Collectors.joining("\\=,\\=")));
    }

    @Test
    public void compareUrlHasSingleGitPrefix() {
        RemoteGitlabServiceImpl svc = new RemoteGitlabServiceImpl(GITLAB_URL, "key", mock(IHttpClient.class));
        String url = svc.compareUrl(new DiffTask(new String[0], 114, "t", "1", "2"));
        assertEquals("https://code.clubber.me/git/api/v4/projects/114/repository/compare?from=paynet-1-jdk21&to=paynet-2-jdk21", url);
    }

    @Test
    public void nonOkResponseThrowsWithStatusAndBody() throws Exception {
        IHttpClient client = mock(IHttpClient.class);
        when(client.send(any(), any())).thenReturn(response(404, "{\"error\":\"404 Not Found\"}"));
        RemoteGitlabServiceImpl svc = new RemoteGitlabServiceImpl(GITLAB_URL, "key", client);
        try {
            svc.getTagDiff(new DiffTask(new String[0], 114, "t", "1", "2"));
            fail("expected failure on non-200");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("404"));
            assertTrue(e.getMessage(), e.getMessage().contains("Not Found"));
        }
    }

    @Test
    public void unparseableOkBodyThrowsWithBody() throws Exception {
        IHttpClient client = mock(IHttpClient.class);
        when(client.send(any(), any())).thenReturn(response(200, "\"oops\"")); // 200 but not the expected object
        RemoteGitlabServiceImpl svc = new RemoteGitlabServiceImpl(GITLAB_URL, "key", client);
        try {
            svc.getTagDiff(new DiffTask(new String[0], 114, "t", "1", "2"));
            fail("expected failure on unparseable body");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("oops"));
        }
    }

    private static HttpResponse response(int status, String body) {
        return new HttpResponse(status, "", Collections.emptyList(), body.getBytes(UTF_8));
    }
}
