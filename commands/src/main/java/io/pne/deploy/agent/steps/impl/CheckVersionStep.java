package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.commands.VersionChecks;
import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import io.pne.deploy.agent.steps.policy.UrlGuard;

import java.io.IOException;

/** Refuses a plan that would move an application to a version older than the one it reports today. */
public class CheckVersionStep implements IStep {

    public static final String TYPE = "check-version";

    private final String url;
    private final String version;
    private final int    timeoutSeconds;

    public CheckVersionStep(StepParams aParams) throws StepValidationException {
        url            = aParams.required("url");
        version        = aParams.required("version");
        timeoutSeconds = aParams.intValue("timeoutSeconds", 30);
        if (timeoutSeconds <= 0) {
            throw aParams.error("timeoutSeconds", "must be positive, got " + timeoutSeconds);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "url", url);
        aScope.checkReferences(TYPE, "version", version);
        UrlGuard.check(aPolicy.getStatusHosts(), TYPE, "url", StepPlanScope.withPlaceholders(url, "0"));
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        try {
            String expandedUrl     = aContext.expand(url);
            String expandedVersion = aContext.expand(version);
            UrlGuard.check(aContext.getPolicy().getStatusHosts(), TYPE, "url", expandedUrl);

            VersionChecks.checkNotOlder(expandedUrl, expandedVersion, aContext::log);

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot read the current version from " + url + ": " + e, e);
        }
    }

    @Override
    public long getMaxSeconds() {
        return timeoutSeconds;
    }
}
