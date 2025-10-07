package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.http.client.api.*;
import com.payneteasy.http.client.impl.HttpClientImpl;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.data_model.*;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineComment;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineIssue;
import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.payneteasy.http.client.api.HttpHeaders.singleHeader;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteRedmine4_2_10ServiceImpl implements IRemoteRedmineService {

    private static final Logger LOG = LoggerFactory.getLogger( RemoteRedmine4_2_10ServiceImpl.class );

    private static final String X_REDMINE_API_KEY = "X-Redmine-API-Key";
    private static final String ISSUES = "/issues/";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final IRedmineRemoteConfig  config;
    private final IHttpClient           client;
    private final Gson                  gson;
    private final HttpRequestParameters requestParameters = HttpRequestParameters.builder().timeouts(new HttpTimeouts(20_000, 20_000)).build();

    public RemoteRedmine4_2_10ServiceImpl(IRedmineRemoteConfig aConfig) {
        client = new HttpClientImpl();
        config = aConfig;
        gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public List<RedmineComment> getComments(int aIssueId) {
        LOG.info("getComments({})", aIssueId);
        String requestURL = config.url() + ISSUES + aIssueId + ".json?include=journals";

        RedmineIssueData redmineIssueData = fetchRedmineIssueData(aIssueId, requestURL);

        return redmineIssueData.getJournals()
                .stream()
                .map(journal -> ImmutableRedmineComment.builder()
                        .userId    ( journal.getUser().getId() )
                        .text      ( journal.getNotes()        )
                        .commentId ( journal.getId()           )
                        .build()
                )
                .collect(Collectors.<RedmineComment>toList());

    }

    private RedmineIssueData fetchRedmineIssueData(long aIssueId, String requestURL) {
        HttpRequest request = HttpRequest.builder()
                .url     ( requestURL )
                .headers ( singleHeader(X_REDMINE_API_KEY, config.apiAccessKey()))
                .method  ( HttpMethod.GET )
                .build();

        HttpResponse response;
        try {
            response = client.send(request, requestParameters);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send request to " + requestURL, e);
        }

        String responseBody = new String(response.getBody(), UTF_8);

        if (response.getStatusCode() != 200) {
            throw new IllegalStateException(
                    "Can't get comments for issue " + aIssueId
                            + ". Bad URL: " + requestURL
                            + "\nStatus: " + response.getStatusCode()
                            + "\nResponse body: " + responseBody
            );
        }

        RootIssueData    rootIssueData    = gson.fromJson(responseBody, RootIssueData.class);
        return rootIssueData.getIssue();
    }

    private void changeStatus(int aRedmineIssueId, int aNewStatus, String aMessage) {
        if (aRedmineIssueId == 0) {
            LOG.warn("Can't change status for issue {}. Issue ID is 0", aRedmineIssueId);
            return;
        }

        UpdateIssue issue = new UpdateIssue();
        issue.setId(aRedmineIssueId);
        issue.setStatusId(aNewStatus);
        issue.setNotes(aMessage);

        String requestURL = config.url() + ISSUES + aRedmineIssueId + ".json";

        String requestBody = gson.toJson(new RootUpdateIssue(issue));

        LOG.debug("Sending request to {} with body {}", requestURL, requestBody);

        HttpRequest request = HttpRequest.builder()
                .url     (requestURL)
                .headers(new HttpHeaders(Arrays.asList(
                          new HttpHeader(X_REDMINE_API_KEY, config.apiAccessKey())
                        , new HttpHeader(CONTENT_TYPE, APPLICATION_JSON)
                )))
                .method  ( HttpMethod.PUT )
                .body    ( requestBody.getBytes(UTF_8) )
                .build();

        HttpResponse response;
        try {
            response = client.send(request, requestParameters);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send request to " + requestURL, e);
        }

        String responseBody = new String(response.getBody(), UTF_8);

        if (response.getStatusCode() != 204) {
            throw new IllegalStateException(
                    "Cannot update issue (" + requestURL + ")"
                    + "\nStatus: " + response.getStatusCode()
                    + "\nResponse Body: " + responseBody
            );
        }

    }

    @Override
    public void changeStatusFromAcceptedToProcessing(int aRedmineIssueId, String aMessage) {
        LOG.info("changeStatusFromAcceptedToProcessing({}, {})", aRedmineIssueId, aMessage);
        changeStatus(aRedmineIssueId, config.statusProcessingId(), aMessage);
    }

    @Override
    public void changeStatusToDone(int aRedmineIssueId, String aMessage) {
        LOG.info("changeStatusToDone({}, {})", aRedmineIssueId, aMessage);
        changeStatus(aRedmineIssueId, config.statusDoneId(), aMessage);
    }

    @Override
    public void changeStatusToFailed(int aRedmineIssueId, String aMessage) {
        LOG.info("changeStatusToFailed({}, {})", aRedmineIssueId, aMessage);
        changeStatus(aRedmineIssueId, config.statusFailedId(), aMessage);
    }

    @Override
    public void addComment(int aIssueId, String aMessage) {
        LOG.info("addComment({}, {})", aIssueId, aMessage);
        RedmineIssue redmineIssue = getIssue(aIssueId);
        changeStatus(aIssueId, redmineIssue.statusId(), aMessage);
    }

    private ImmutableRedmineIssue mapIssue(RedmineIssueData issue) {
        Map<String, String> customFields = new HashMap<>();
        for (CustomFields redmineCustomField : issue.getCustomFields()) {
            customFields.put(redmineCustomField.getName(), redmineCustomField.getValue());
        }
        return ImmutableRedmineIssue.builder()
                .issueId        ( issue.getId())
                .description    ( issue.getDescription())
                .subject        ( issue.getSubject())
                .statusName     ( issue.getStatus().getName())
                .statusId       ( issue.getStatus().getId())
                .projectId      ( issue.getProject().getId())
                .projectName    ( issue.getProject().getName())
                .customFields   ( customFields)
                .assigneeName   ( issue.getAssigned_to().getName())
                .creatorName    ( issue.getAuthor().getName())
                .build();
    }

    @Override
    public RedmineIssue getIssue(long aIssueId) {
        LOG.info("getIssue({})", aIssueId);
        String requestURL = config.url() + ISSUES + aIssueId + ".json";

        RedmineIssueData redmineIssueData = fetchRedmineIssueData(aIssueId, requestURL);

        return mapIssue(redmineIssueData);
    }
}
