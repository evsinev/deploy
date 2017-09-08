package io.pne.deploy.client.redmine.remote.impl;

import com.taskadapter.redmineapi.*;
import com.taskadapter.redmineapi.bean.Issue;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineComment;
import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineIssue;
import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RemoteRedmineServiceImpl implements IRemoteRedmineService {

    private final IssueManager issueManager;
    private final IRedmineRemoteConfig config;

    public RemoteRedmineServiceImpl(IRedmineRemoteConfig aConfig) {
        RedmineManager redmine = RedmineManagerFactory.createWithApiKey(aConfig.url(),  aConfig.apiAccessKey());
        issueManager = redmine.getIssueManager();
        config = aConfig;
    }

    @Override
    public List<RedmineIssue> listAssignedTickets() {
        return getRedmineIssues().stream()
                .map(issue -> ImmutableRedmineIssue.builder()
                        .issueId     ( issue.getId()              )
                        .description ( issue.getDescription()     )
                        .subject     ( issue.getSubject()         )
                        .statusName  ( issue.getStatusName()      )
                        .statusId    ( issue.getStatusId()        )
                        .projectId   ( issue.getProject().getId() )
//                        .comments    ( issue.getgetComments(issue.getId()) )
                        .build())
                .collect(Collectors.<RedmineIssue>toList());
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
