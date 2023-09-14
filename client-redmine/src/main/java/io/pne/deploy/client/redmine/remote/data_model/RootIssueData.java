package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Data;

@Data
public class RootIssueData {
    private RedmineIssueData issue;

    public RootIssueData(RedmineIssueData issue) {
        this.issue = issue;
    }
}

