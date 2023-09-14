package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Data;

@Data
public class RootUpdateIssue {
    private final UpdateIssue issue;

    public RootUpdateIssue(UpdateIssue issue) {
        this.issue = issue;
    }
}
