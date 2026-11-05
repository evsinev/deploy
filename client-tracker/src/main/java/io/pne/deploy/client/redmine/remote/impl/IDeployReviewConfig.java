package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

/**
 * Deploy-review webhook: an outbound POST sent at deploy start so an external service can analyse the
 * release while it is being rolled out. The spool directory is not configured here — it is derived from
 * {@link IRedmineRemoteConfig#queueDir()} so {@code QUEUE_DIR} stays declared in one place.
 */
public interface IDeployReviewConfig extends IStartupConfig {

    @AStartupParameter(name = "DEPLOY_REVIEW_ENABLED", value = "false")
    boolean isEnabled();

    @AStartupParameter(name = "DEPLOY_REVIEW_URL", value = "")
    String url();

    @AStartupParameter(name = "DEPLOY_REVIEW_TOKEN", value = "", maskVariable = true)
    String token();

    @AStartupParameter(name = "DEPLOY_REVIEW_TIMEOUT_MS", value = "20000")
    int timeoutMs();

}
