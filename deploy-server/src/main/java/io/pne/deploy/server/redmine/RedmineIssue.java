package io.pne.deploy.server.redmine;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface RedmineIssue {

    long issueId();

    String description();

    List<RedmineComment> comments();


}
