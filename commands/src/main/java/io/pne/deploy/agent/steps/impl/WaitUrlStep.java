package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.commands.UrlWaiter;
import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import io.pne.deploy.agent.steps.policy.UrlGuard;

/** Waits until an application reports the version that was just deployed, which is how a step plan confirms itself. */
public class WaitUrlStep implements IStep {

    public static final String TYPE = "wait-url";

    private final String  url;
    private final String  version;
    private final int     timeoutSeconds;
    private final int     intervalSeconds;
    private final boolean failOnOtherVersion;

    public WaitUrlStep(StepParams aParams) throws StepValidationException {
        url                = aParams.required("url");
        version            = aParams.required("version");
        timeoutSeconds     = aParams.requiredInt("timeoutSeconds");
        intervalSeconds    = aParams.intValue("intervalSeconds", 1);
        failOnOtherVersion = aParams.boolValue("failOnOtherVersion", true);

        if (timeoutSeconds <= 0) {
            throw aParams.error("timeoutSeconds", "must be positive, got " + timeoutSeconds);
        }
        if (intervalSeconds <= 0) {
            throw aParams.error("intervalSeconds", "must be positive, got " + intervalSeconds);
        }
        if (intervalSeconds > timeoutSeconds) {
            throw aParams.error("intervalSeconds", "must not be longer than timeoutSeconds ("
                    + intervalSeconds + " > " + timeoutSeconds + ")");
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
        if (timeoutSeconds > aPolicy.getMaxStepSeconds()) {
            throw new StepValidationException("step '" + TYPE + "': timeoutSeconds " + timeoutSeconds
                    + " is over the limit of " + aPolicy.getMaxStepSeconds() + "s");
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        try {
            String expandedUrl     = aContext.expand(url);
            String expandedVersion = aContext.expand(version);
            UrlGuard.check(aContext.getPolicy().getStatusHosts(), TYPE, "url", expandedUrl);

            UrlWaiter.waitFor(expandedUrl, expandedVersion, timeoutSeconds, intervalSeconds, failOnOtherVersion,
                    aContext::log);

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepExecutionException("Interrupted while waiting for " + url, e);
        }
    }

    @Override
    public long getMaxSeconds() {
        return timeoutSeconds;
    }
}
