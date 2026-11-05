package io.pne.deploy.client.redmine.remote.model;

import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
public interface RedmineComment {

    @Nullable
    String text();

    long userId();

    long commentId();
}
