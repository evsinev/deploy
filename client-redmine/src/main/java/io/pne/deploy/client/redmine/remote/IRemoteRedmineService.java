package io.pne.deploy.client.redmine.remote;

import io.pne.deploy.client.redmine.remote.model.RedmineComment;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;

import java.util.List;

public interface IRemoteRedmineService {
    List<RedmineIssue> listAssignedTickets();

    List<RedmineComment> getComments(int aIssueId);

    void changeStatusFromAcceptedToProcessing(int aRedmineIssueId, String aMessage);
    void changeStatusToDone(int aRedmineIssueId, String aMessage);
    void changeStatusToFailed(int aRedmineIssueId, String aMessage);

    void addComment(int aIssueId, String aMessage);
}
