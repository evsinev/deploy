package io.pne.deploy.server.redmine;

import org.immutables.value.Value;

@Value.Immutable
public interface RedmineComment {

    String text();

    long userId();
}
