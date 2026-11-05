package io.pne.deploy.client.redmine.remote;

import io.pne.deploy.client.redmine.remote.data_model.DeployReviewRequest;

/** Outbound deploy-review webhook. Delivery is durable and at-least-once — the receiver dedupes by version. */
public interface IRemoteDeployReviewService {

    /** Spool a deploy-start notification and return immediately; the send happens on the sender thread. */
    void enqueueDeployStarted(DeployReviewRequest aRequest);

}
