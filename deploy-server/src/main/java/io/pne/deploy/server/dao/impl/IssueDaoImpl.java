package io.pne.deploy.server.dao.impl;

import io.pne.deploy.server.dao.IIssuesDao;
import io.pne.deploy.server.service.redmine.IssueState;
import io.pne.deploy.server.service.redmine.RedmineIssue;

import java.io.File;

public class IssueDaoImpl implements IIssuesDao {

    private final File issuesDir;

    public IssueDaoImpl(File issuesDir) {
        this.issuesDir = issuesDir;
    }

    @Override
    public void setIssueState(RedmineIssue aIssue, IssueState aState) {

    }

    @Override
    public boolean isNewIssue(RedmineIssue aIssue) {
        return !new File(issuesDir, aIssue.issueId()+"").exists();
    }
}
