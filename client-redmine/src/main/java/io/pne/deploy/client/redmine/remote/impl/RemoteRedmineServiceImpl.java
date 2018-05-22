package io.pne.deploy.client.redmine.remote.impl;

import com.taskadapter.redmineapi.Include;
import com.taskadapter.redmineapi.IssueManager;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.CustomField;
import com.taskadapter.redmineapi.bean.Issue;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineComment;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineIssue;
import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.taskadapter.redmineapi.RedmineManagerFactory.createWithApiKey;

public class RemoteRedmineServiceImpl implements IRemoteRedmineService {

    private final IssueManager issueManager;
    private final IRedmineRemoteConfig config;

    public RemoteRedmineServiceImpl(IRedmineRemoteConfig aConfig) {
        HttpClient     httpClient = createHttpClient(aConfig.connectTimeoutSeconds(), aConfig.readTimeoutSeconds());
        RedmineManager redmine    = createWithApiKey(aConfig.url(),  aConfig.apiAccessKey(), httpClient);

        issueManager = redmine.getIssueManager();
        config       = aConfig;
    }

    private HttpClient createHttpClient(int aConnectTimeout, int aReadTimeout) {

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout           ( aConnectTimeout * 1_000 )
                .setConnectionRequestTimeout ( aConnectTimeout * 1_000 )
                .setSocketTimeout            ( aReadTimeout    * 1_000 )
                .build();
        return HttpClientBuilder.create().setDefaultRequestConfig(config).build();
    }

    @Override
    public List<RedmineIssue> listAssignedTickets() {
        return getRedmineIssues().stream()
                .map(this::mapIssue)
                .collect(Collectors.<RedmineIssue>toList());
    }

    private ImmutableRedmineIssue mapIssue(Issue issue) {

        Map<String, String> customFields = new HashMap<>();
        Collection<CustomField> redmineCustomFields = issue.getCustomFields();
        for (CustomField redmineCustomField : redmineCustomFields) {
            customFields.put(redmineCustomField.getName(), redmineCustomField.getValue());
        }

        return ImmutableRedmineIssue.builder()
                .issueId      ( issue.getId()                     )
                .description  ( issue.getDescription()            )
                .subject      ( issue.getSubject()                )
                .statusName   ( issue.getStatusName()             )
                .statusId     ( issue.getStatusId()               )
                .projectId    ( issue.getProject().getId()        )
                .projectName  ( issue.getProject().getName()      )
                .customFields ( customFields                      )
                .assigneeName ( issue.getAssignee().getFullName() )
                .creatorName  ( issue.getAuthor().getFullName()   )
                .build();
    }

    public List<RedmineComment> getComments(int aIssueId) {
        Issue issue;
        try {
            issue = issueManager.getIssueById(aIssueId, Include.journals);
        } catch (RedmineException e) {
            throw new IllegalStateException("Could not get comments for issue "+aIssueId, e);
        }

        return issue.getJournals().stream()
                .map(journal -> ImmutableRedmineComment.builder()
                        .userId    ( journal.getUser().getId() )
                        .text      ( journal.getNotes()        )
                        .commentId ( journal.getId()           )
                        .build())
                .collect(Collectors.<RedmineComment>toList());
    }

    @Override
    public void changeStatusToDone(int aRedmineIssueId, String aMessage) {
        changeStatus(aRedmineIssueId, config.statusDoneId(), aMessage, oldStatus -> {});
    }

    @Override
    public void changeStatusToFailed(int aRedmineIssueId, String aMessage) {
        changeStatus(aRedmineIssueId, config.statusFailedId(), aMessage, oldStatus -> {});
    }

    @Override
    public void changeStatusFromAcceptedToProcessing(int aRedmineIssueId, String aMessage) throws IllegalStateException{
        changeStatus(aRedmineIssueId, config.statusProcessingId(), aMessage, oldStatus -> {
            if(oldStatus != config.statusAcceptedId()) {
                throw new IllegalStateException("Status must be ACCEPT (" + config.statusAcceptedId() +") but it was " + oldStatus );
            }
        });
    }

    @Override
    public void addComment(int aIssueId, String aMessage) {
        Issue issue;
        try {
            issue = issueManager.getIssueById(aIssueId);
        } catch (RedmineException e) {
            throw new IllegalStateException("Cannot get issue " + aIssueId, e);
        }
        issue.setNotes(aMessage);

        try {
            issueManager.update(issue);
        } catch (RedmineException e) {
            throw new IllegalStateException("Cannot add comment to  " + issue.getStatusId());
        }

    }

    @Override
    public RedmineIssue getIssue(long aIssueId) {
        try {
            Issue issue = issueManager.getIssueById((int) aIssueId);
            return mapIssue(issue);
        } catch (RedmineException e) {
            throw new IllegalStateException("Cannot convert Issue " + aIssueId + " to RedmineIssue", e);
        }
    }

    private List<Issue> getRedmineIssues() {
        try {
            return issueManager.getIssues(config.issuesQueryParameters());
        } catch (RedmineException e) {
            throw new IllegalStateException("Could not get issues", e);
        }
    }

    private void changeStatus(int aRedmineIssueId, int aNewStatus, String aMessage, Consumer<Integer> aCheckCurrentStatus) {
        Issue issue;
        try {
            issue = issueManager.getIssueById(aRedmineIssueId);
        } catch (RedmineException e) {
            throw new IllegalStateException("Cannot get issue " + aRedmineIssueId, e);
        }

        int oldStatus = issue.getStatusId();
        aCheckCurrentStatus.accept(oldStatus);

        issue.setStatusId(aNewStatus);
        issue.setNotes(aMessage);

        try {
            issueManager.update(issue);
        } catch (RedmineException e) {
            throw new IllegalStateException("Cannot change status from " + oldStatus + " to " + aNewStatus);
        }
    }

}
