package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.data_model.*;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineComment;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineIssue;
import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import okhttp3.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class RemoteRedmine4_2_10ServiceImpl implements IRemoteRedmineService {

    private static final String X_REDMINE_API_KEY = "X-Redmine-API-Key";
    private static final String CANT_GET_ISSUE_EXCEPTION = "Can't get issue. Bad URL: ";
    private static final String CANT_PASE_ISSUE_FROM_EXCEPTION = "Can't parse issue from ";
    private static final String CANT_GET_ISSUE_FROM_EXCEPTION = "Can't get issue from ";
    private static final String CANT_CREATE_ISSUE_EXCEPTION = "Can't create issue at ";
    private static final String CANT_UPDATE_ISSUE_EXCEPTION = "Can't update issue ";
    private static final String PARAMETERS = "\nIssue ID: ";
    private static final String ISSUES = "/issues/";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final IRedmineRemoteConfig config;
    private final OkHttpClient         client;
    private final Gson                 gson;

    public RemoteRedmine4_2_10ServiceImpl(IRedmineRemoteConfig aConfig) {
        client = new OkHttpClient().newBuilder().build();
        config = aConfig;
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public List<RedmineComment> getComments(int aIssueId) {
        String requestURL = config.url() + ISSUES + aIssueId + ".json?include=journals";
        try {
            Request request = new Request.Builder()
                    .url(new URL(requestURL))
                    .addHeader(X_REDMINE_API_KEY, config.apiAccessKey())
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                RedmineIssueData redmineIssueData = gson.fromJson(response.body().string(), RootIssueData.class).getIssue();
                return redmineIssueData.getJournals().stream().map(journal -> ImmutableRedmineComment.builder().userId(journal.getUser().getId()).text(journal.getNotes()).commentId(journal.getId()).build()).collect(Collectors.<RedmineComment>toList());
            }
        } catch (IllegalStateException | JsonSyntaxException | NullPointerException e) {
            throw new IllegalStateException(CANT_PASE_ISSUE_FROM_EXCEPTION + requestURL);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(CANT_GET_ISSUE_EXCEPTION + requestURL);
        } catch (IOException e) {
            throw new IllegalStateException(CANT_GET_ISSUE_FROM_EXCEPTION + requestURL);
        }
    }

    private void changeStatus(int aRedmineIssueId, int aNewStatus, String aMessage) {
        if (aRedmineIssueId == 0) {
            return;
        }
        UpdateIssue issue = new UpdateIssue();
        issue.setId(aRedmineIssueId);
        issue.setStatusId(aNewStatus);
        issue.setNotes(aMessage);
        String requestURL = config.url() + ISSUES + aRedmineIssueId + ".json";
        RequestBody requestBody = RequestBody
                .create(gson.toJson(new RootUpdateIssue(issue)), MediaType.parse(APPLICATION_JSON + "; charset=utf-8"));
        try {
            Request request = new Request.Builder()
                    .url(new URL(requestURL))
                    .addHeader(X_REDMINE_API_KEY, config.apiAccessKey())
                    .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .put(requestBody)
                    .build();
            try(Response response = client.newCall(request).execute()) {
                if (!String.valueOf(response.code()).startsWith("20")) {
                    throw new IllegalStateException(CANT_UPDATE_ISSUE_EXCEPTION + "(" + requestURL + ")\nStatus: " + response.code() + PARAMETERS + issue.getId());
                }
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(CANT_GET_ISSUE_EXCEPTION + requestURL);
        } catch (IOException e) {
            throw new IllegalStateException(CANT_UPDATE_ISSUE_EXCEPTION + "(" + requestURL + ")\nNo response status\nIssue ID: " + issue.getId());
        }
    }

    @Override
    public void changeStatusFromAcceptedToProcessing(int aRedmineIssueId, String aMessage) {
        changeStatus(aRedmineIssueId, config.statusProcessingId(), aMessage);
    }

    @Override
    public void changeStatusToDone(int aRedmineIssueId, String aMessage) {
        changeStatus(aRedmineIssueId, config.statusDoneId(), aMessage);
    }

    @Override
    public void changeStatusToFailed(int aRedmineIssueId, String aMessage) {
        changeStatus(aRedmineIssueId, config.statusFailedId(), aMessage);
    }

    @Override
    public void addComment(int aIssueId, String aMessage) {
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
        String requestURL = config.url() + ISSUES + aIssueId + ".json";
        try {
            Request request = new Request.Builder()
                    .url(new URL(requestURL))
                    .addHeader(X_REDMINE_API_KEY, config.apiAccessKey())
                    .get()
                    .build();

            try(Response response = client.newCall(request).execute()) {
                RedmineIssueData redmineIssueData = gson.fromJson(response.body().string(), RootIssueData.class).getIssue();
                return mapIssue(redmineIssueData);
            }
        } catch (IllegalStateException | JsonSyntaxException | NullPointerException e) {
            throw new IllegalStateException(CANT_PASE_ISSUE_FROM_EXCEPTION + requestURL);
        } catch (MalformedURLException e) {
            throw new IllegalStateException(CANT_GET_ISSUE_EXCEPTION + requestURL);
        } catch (IOException e) {
            throw new IllegalStateException(CANT_GET_ISSUE_FROM_EXCEPTION + requestURL);
        }
    }
}
