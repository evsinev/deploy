package io.pne.deploy.client.redmine.remote.impl;

import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
public interface IRedmineRemoteConfig {

    String url();

    String apiAccessKey();

    Map<String, String> issuesQueryParameters();

    int statusAcceptedId();
    int statusProcessingId();
    int statusDoneId();
    int statusFailedId();

}
