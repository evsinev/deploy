package io.pne.deploy.server.service.redmine;

import org.immutables.value.Value;

@Value.Immutable
public interface RedmineComment {

    String text();

    long userId();

    long commentId();
}
