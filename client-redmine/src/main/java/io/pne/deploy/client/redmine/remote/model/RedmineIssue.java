package io.pne.deploy.client.redmine.remote.model;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface RedmineIssue {

    int issueId();

    String subject();

    String statusName();
    int    statusId();

    String description();

    int projectId();

}
