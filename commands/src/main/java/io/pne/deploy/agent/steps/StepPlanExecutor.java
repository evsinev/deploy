package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a plan of typed steps.
 *
 * <p>The whole plan is built and checked against the policy first, and only then executed, so anything that can be
 * decided up front - an unknown step, a destination outside the allowed roots, a source nobody allows - stops the
 * plan before it has any effect. A value the plan only learns while it runs is checked again at the moment it
 * becomes known, which can stop the plan part way through.
 *
 * <p>Execution stops at the first failing step and nothing is undone, which is the contract a sequential script
 * had as well.
 */
public class StepPlanExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(StepPlanExecutor.class);

    private final StepRegistry registry;
    private final StepPolicy   policy;

    public StepPlanExecutor(StepRegistry aRegistry, StepPolicy aPolicy) {
        registry = aRegistry;
        policy   = aPolicy;
    }

    /** Builds and checks the plan, reporting every problem found rather than only the first one. */
    public List<IStep> prepare(List<AgentStep> aSteps) throws StepValidationException {
        if (aSteps == null || aSteps.isEmpty()) {
            throw new StepValidationException("The plan has no steps");
        }

        List<IStep>   steps    = new ArrayList<>();
        List<String>  problems = new ArrayList<>();
        StepPlanScope scope    = new StepPlanScope();

        for (int i = 0; i < aSteps.size(); i++) {
            String position = position(i, aSteps.size());
            IStep  step     = null;
            try {
                step = registry.create(aSteps.get(i));
            } catch (StepValidationException e) {
                problems.add(position + " " + e.getMessage());
            }
            steps.add(step);

            if (step != null) {
                try {
                    step.validate(policy, scope);
                } catch (StepValidationException e) {
                    problems.add(position + " " + e.getMessage());
                }
            }
        }

        long planSeconds = 0;
        for (IStep step : steps) {
            if (step != null) {
                planSeconds += step.getMaxSeconds();
            }
        }
        if (planSeconds > policy.getMaxPlanSeconds()) {
            problems.add("the plan may take up to " + planSeconds + "s, which is over the limit of "
                    + policy.getMaxPlanSeconds() + "s");
        }

        if (!problems.isEmpty()) {
            throw new StepValidationException("The plan was refused:" + System.lineSeparator()
                    + String.join(System.lineSeparator(), problems));
        }
        return steps;
    }

    public void run(List<AgentStep> aSteps, IStepLog aLog) throws StepValidationException, StepExecutionException {
        List<IStep>  steps   = prepare(aSteps);
        StepContext  context = new StepContext(policy, aLog);

        aLog.log("plan: " + steps.size() + " step(s)");

        for (int i = 0; i < steps.size(); i++) {
            IStep  step     = steps.get(i);
            String position = position(i, steps.size());
            long   started  = System.currentTimeMillis();

            aLog.log(position + " " + step.getType() + " started");
            LOG.info("{} {} started", position, step.getType());
            try {
                step.execute(context);
            } catch (StepExecutionException e) {
                aLog.log(position + " " + step.getType() + " FAILED after " + seconds(started) + ": " + e.getMessage());
                throw e;
            } catch (RuntimeException e) {
                aLog.log(position + " " + step.getType() + " FAILED after " + seconds(started) + ": " + e);
                throw new StepExecutionException(position + " " + step.getType() + " failed: " + e, e);
            }
            aLog.log(position + " " + step.getType() + " ok in " + seconds(started));
        }
    }

    private static String position(int aIndex, int aTotal) {
        return "[" + (aIndex + 1) + "/" + aTotal + "]";
    }

    private static String seconds(long aStartedMillis) {
        return String.format("%.1fs", (System.currentTimeMillis() - aStartedMillis) / 1000.0);
    }
}
