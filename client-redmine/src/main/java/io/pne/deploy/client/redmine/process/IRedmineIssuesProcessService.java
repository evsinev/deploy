package io.pne.deploy.client.redmine.process;

public interface IRedmineIssuesProcessService {

    void processRedmineIssues();

    void processRedmineIssue(long aIssueId);
}
