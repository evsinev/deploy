package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.steps.policy.StepPolicy;

/**
 * One typed action of a plan.
 *
 * <p>A step is built from its parameters, then validated against the policy, and only then executed. The executor
 * validates every step of a plan before running the first one.
 *
 * <p>A parameter that still holds a variable cannot be fully judged up front, so it is checked with a stand-in
 * value and then checked again for real in {@link #execute(StepContext)}. Nothing outside the policy is ever
 * acted on; what a late check costs is only that the plan may stop part way rather than before it started.
 */
public interface IStep {

    String getType();

    /**
     * Static checks: parameter shapes and everything the policy can decide without running anything. Values that
     * depend on a variable produced at run time are checked again in {@link #execute(StepContext)}.
     */
    void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException;

    void execute(StepContext aContext) throws StepExecutionException;

    /** Upper bound on how long this step may take, used to keep a plan inside the response budget. 0 = instant. */
    default long getMaxSeconds() {
        return 0;
    }
}
