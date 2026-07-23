package io.pne.deploy.client.redmine.remote;

import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;

import java.util.List;

public interface IRemoteRedmineService {
    List<RedmineComment> getComments(int aIssueId);

    void enqueueChangeStatusFromAcceptedToProcessing(int aRedmineIssueId, String aMessage);
    void enqueueChangeStatusToDone(int aRedmineIssueId, String aMessage);
    void enqueueChangeStatusToFailed(int aRedmineIssueId, String aMessage);

    void enqueueAddComment(int aIssueId, String aMessage);

    RedmineIssue getIssue(long aIssueId);
}
