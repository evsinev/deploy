package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;

/**
 * Waits before the next step.
 *
 * <p>A pause is a step of its own rather than an attribute of its neighbours: it is a visible event on the timeline,
 * and the reason for it belongs next to it. Zero seconds is allowed and does nothing, which lets one shared plan
 * serve applications that need a pause and applications that do not.
 */
public class SleepStep implements IStep {

    public static final String TYPE = "sleep";

    private final int    seconds;
    private final String reason;

    public SleepStep(StepParams aParams) throws StepValidationException {
        seconds = aParams.intValue("seconds", 0);
        reason  = aParams.optional("reason", "");
        if (seconds < 0) {
            throw aParams.error("seconds", "must not be negative, got " + seconds);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        if (seconds > aPolicy.getMaxSleepSeconds()) {
            throw new StepValidationException("step '" + TYPE + "': " + seconds
                    + "s is over the limit of " + aPolicy.getMaxSleepSeconds() + "s");
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        if (seconds == 0) {
            aContext.log("sleep 0s, skipped" + describeReason());
            return;
        }
        aContext.log("sleeping " + seconds + "s" + describeReason());
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepExecutionException("Interrupted while sleeping", e);
        }
    }

    @Override
    public long getMaxSeconds() {
        return seconds;
    }

    private String describeReason() {
        return reason.isEmpty() ? "" : " (" + reason + ")";
    }
}
