package io.pne.deploy.client.redmine.process.data_model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DiffLink {
    private String commitMessage;
    private LocalDate commitDate;
    private Integer redmineIssueId;
    private String redmineUrl;
    private String redmineIssueSubject;
}
