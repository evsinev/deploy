package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.http.client.api.*;
import com.payneteasy.http.client.impl.HttpClientImpl;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.data_model.DiffCommit;
import io.pne.deploy.client.redmine.remote.data_model.GitlabDiffData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteGitlabServiceImpl implements IRemoteGitlabService {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteGitlabServiceImpl.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String AUTHORIZATION_HEADER_PART = "Bearer ";
    private static final String API_1 = "/api/v4/projects/"; // GITLAB_URL already carries the custom /git prefix
    private static final String API_2 = "/repository/compare?";
    private static final String FROM = "from=";
    private static final String TO = "&to=";

    private final String gitlabUrl;
    private final String gitlabApiKey;
    private final IHttpClient client;
    private final Gson gson;
    private final HttpRequestParameters requestParameters = HttpRequestParameters.builder().timeouts(new HttpTimeouts(20_000, 20_000)).build();

    public RemoteGitlabServiceImpl(IRedmineRemoteConfig aConfig) {
        this(aConfig.gitlabUrl(), aConfig.gitlabApiKey(), new HttpClientImpl());
    }

    /** Injectable constructor for tests (mock the HTTP client). */
    RemoteGitlabServiceImpl(String aGitlabUrl, String aGitlabApiKey, IHttpClient aClient) {
        gitlabUrl = aGitlabUrl;
        gitlabApiKey = aGitlabApiKey;
        client = aClient;
        gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public List<DiffCommit> getTagDiff(DiffTask diffTask) {
        LOG.info("getTagDiff({})", diffTask);
        GitlabDiffData gitlabDiffData = fetchGitlabDiffData(diffTask);
        List<DiffCommit> commits = new ArrayList<>();
        if (gitlabDiffData == null || gitlabDiffData.getCommits() == null || gitlabDiffData.getCommits().isEmpty()) {
            return commits;
        }
        return gitlabDiffData.getCommits()
                .stream()
                .filter(c -> c != null && c.getMessage() != null && !c.getMessage().trim().isEmpty())
                .filter(c -> !c.getMessage().contains("[maven-release-plugin][skip ci]"))
                .map(c -> new DiffCommit(c.getMessage().trim(), parseDate(c.getCommitterDate())))
                .collect(Collectors.toList());
    }

    /** GitLab commit dates are ISO-8601 with an offset (e.g. {@code 2026-07-20T12:34:56.000+03:00}); null on absent/bad. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (RuntimeException e) {
            LOG.warn("Gitlab compare: can't parse commit date '{}'", value);
            return null;
        }
    }

    private GitlabDiffData fetchGitlabDiffData(DiffTask diffTask) {
        String requestUrl = compareUrl(diffTask);
        LOG.info("Gitlab compare: GET {}", requestUrl); // the api key is in the Authorization header, not the url

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
        int    status = response.getStatusCode();
        String body   = new String(response.getBody(), UTF_8);
        if (status != 200) {
            LOG.warn("Gitlab compare: HTTP {} for {} — body: {}", status, requestUrl, snippet(body));
            throw new IllegalStateException("Gitlab compare HTTP " + status + " for project " + diffTask.getGitlabProject()
                    + " (" + diffTask.getOldVersion() + " -> " + diffTask.getNewVersion() + "): " + snippet(body));
        }
        try {
            GitlabDiffData data = gson.fromJson(body, GitlabDiffData.class);
            LOG.info("Gitlab compare: HTTP {}, {} commit(s) for project {}",
                    status, data == null || data.getCommits() == null ? 0 : data.getCommits().size(), diffTask.getGitlabProject());
            return data;
        } catch (RuntimeException e) {
            LOG.warn("Gitlab compare: HTTP {} but body is not valid JSON for {} — body: {}", status, requestUrl, snippet(body));
            throw new IllegalStateException("Gitlab compare unparseable body (HTTP " + status + ") for project "
                    + diffTask.getGitlabProject() + ": " + snippet(body), e);
        }
    }

    /** GitLab "compare two tags" API url. {@code GITLAB_URL} already carries the custom prefix, so API_1 must not. */
    String compareUrl(DiffTask diffTask) {
        return gitlabUrl + API_1 + diffTask.getGitlabProject() + API_2
                + FROM + getTagFromVersion(diffTask.getOldVersion())
                + TO + getTagFromVersion(diffTask.getNewVersion());
    }

    /** First ~500 chars of the response body, stripped, for error logs; never includes request headers. */
    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) + "…(" + trimmed.length() + " chars)" : trimmed;
    }

    private String getTagFromVersion(String version) {
        return "paynet-" + version + "-jdk21";
    }
}
