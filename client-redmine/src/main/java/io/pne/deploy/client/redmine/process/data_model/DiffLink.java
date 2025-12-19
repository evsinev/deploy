package io.pne.deploy.client.redmine.process.data_model;

import lombok.Data;

@Data
public class DiffLink {
    private String commitMessage;
    private Integer redmineIssueId;
    private String redmineUrl;
    private String redmineIssueSubject;
}
