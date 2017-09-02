package io.pne.deploy.server.dao;


import io.pne.deploy.server.service.redmine.IssueState;
import io.pne.deploy.server.service.redmine.RedmineIssue;

public interface IIssuesDao {

    void setIssueState(RedmineIssue aIssue, IssueState aState);

    boolean isNewIssue(RedmineIssue aIssue);
}
