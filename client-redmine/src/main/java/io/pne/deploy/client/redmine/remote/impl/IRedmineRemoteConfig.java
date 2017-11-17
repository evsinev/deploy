package io.pne.deploy.client.redmine.remote.impl;

import io.pne.deploy.util.env.IStartupConfig;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
public interface IRedmineRemoteConfig extends IStartupConfig {

    String url();

    String apiAccessKey();

    Map<String, String> issuesQueryParameters();

    int statusAcceptedId();
    int statusProcessingId();
    int statusDoneId();
    int statusFailedId();

    int connectTimeoutSeconds();
    int readTimeoutSeconds();
}
