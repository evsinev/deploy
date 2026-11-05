package io.pne.deploy.client.redmine.remote.model;

import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
public interface RedmineIssue {

    int issueId();

    String subject();

    String statusName();
    int    statusId();

    String description();

    int projectId();

    Map<String, String> customFields();

    String projectName();

    String assigneeName();

    String creatorName();

}
