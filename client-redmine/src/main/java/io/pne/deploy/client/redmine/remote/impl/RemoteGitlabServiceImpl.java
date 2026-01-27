package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.http.client.api.*;
import com.payneteasy.http.client.impl.HttpClientImpl;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.data_model.Commit;
import io.pne.deploy.client.redmine.remote.data_model.GitlabDiffData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteGitlabServiceImpl implements IRemoteGitlabService {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteGitlabServiceImpl.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_HEADER_PART = "Bearer ";
    private static final String API_1 = "/git/api/v4/projects/";
    private static final String API_2 = "/repository/compare?";
    private static final String FROM = "from=";
    private static final String TO = "&to=";

    private final String gitlabUrl;
    private final String gitlabApiKey;
    private final IHttpClient client;
    private final Gson gson;
    private final HttpRequestParameters requestParameters = HttpRequestParameters.builder().timeouts(new HttpTimeouts(20_000, 20_000)).build();

    public RemoteGitlabServiceImpl(IRedmineRemoteConfig aConfig) {
        gitlabUrl = aConfig.gitlabUrl();
        gitlabApiKey = aConfig.gitlabApiKey();
        client = new HttpClientImpl();
        gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public List<String> getTagDiff(DiffTask diffTask) {
        LOG.info("getTagDiff({})", diffTask);
        GitlabDiffData gitlabDiffData = fetchGitlabDiffData(diffTask);
        List<String> messages = new ArrayList<>();
        if (gitlabDiffData == null || gitlabDiffData.getCommits() == null || gitlabDiffData.getCommits().isEmpty()) {
            return messages;
        }
        return gitlabDiffData.getCommits()
                .stream()
                .map(Commit::getMessage)
                .map(s-> s == null ? null : s.trim())
                .filter(s -> s != null && !s.isEmpty())
                .filter(s -> !s.contains("[maven-release-plugin][skip ci]"))
                .collect(Collectors.toList());
    }

    private GitlabDiffData fetchGitlabDiffData(DiffTask diffTask) {
        String requestUrl = gitlabUrl + API_1 + diffTask.getGitlabProject() + API_2 + FROM + getTagFromVersion(diffTask.getOldVersion()) + TO + getTagFromVersion(diffTask.getNewVersion());

        HttpRequest request = HttpRequest.builder()
                .url(requestUrl)
                .headers(HttpHeaders.singleHeader(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_PART + gitlabApiKey))
                .method(HttpMethod.GET)
                .build();
        HttpResponse response;
        try {
            response = client.send(request, requestParameters);
        } catch (Exception e) {
            throw new IllegalStateException("Can't send request to " + requestUrl, e);
        }
        String responseBody = new String(response.getBody(), UTF_8);
        if (response.getStatusCode() != 200) {
            throw new IllegalStateException("Can't get diff for tags " + diffTask.getOldVersion() + " and " + diffTask.getNewVersion() + " for project " + diffTask.getGitlabProject());
        }
        return gson.fromJson(responseBody, GitlabDiffData.class);
    }

    private String getTagFromVersion(String version) {
        return "paynet-" + version + "-jdk21";
    }
}
