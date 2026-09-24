package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StepPlanExecutorTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void runsStepsInOrder() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();

        StepTestSupport.CollectingLog log = run(root, Arrays.asList(
                step("write-file", "path", root.resolve("first.txt").toString(),  "content", "1"),
                step("write-file", "path", root.resolve("second.txt").toString(), "content", "2")));

        assertTrue(Files.exists(root.resolve("first.txt")));
        assertTrue(Files.exists(root.resolve("second.txt")));
        assertTrue(log.toString(), log.hasLineContaining("[1/2] write-file ok"));
        assertTrue(log.toString(), log.hasLineContaining("[2/2] write-file ok"));
    }

    @Test
    public void refusesTheWholePlanBeforeRunningAnyStep() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));

        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(allowed.toString()))
                .build();

        List<AgentStep> plan = Arrays.asList(
                step("write-file", "path", allowed.resolve("first.txt").toString(), "content", "1"),
                step("write-file", "path", root.resolve("outside.txt").toString(),  "content", "2"));

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy).run(plan, StepTestSupport.log());
            fail("expected the plan to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("[2/2]"));
        }

        assertFalse("a refused plan must not have run its first step", Files.exists(allowed.resolve("first.txt")));
    }

    @Test
    public void reportsEveryProblemAtOnce() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();

        List<AgentStep> plan = Arrays.asList(
                step("no-such-step"),
                step("write-file", "content", "no path here"));

        try {
            run(root, plan);
            fail("expected the plan to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unknown step type 'no-such-step'"));
            assertTrue(e.getMessage(), e.getMessage().contains("parameter 'path' is required"));
        }
    }

    @Test
    public void stopsAtTheFirstFailingStep() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        Path missing = root.resolve("missing.txt");

        List<AgentStep> plan = Arrays.asList(
                step("copy-file", "from", missing.toString(), "to", root.resolve("copy.txt").toString()),
                step("write-file", "path", root.resolve("after.txt").toString(), "content", "x"));

        try {
            run(root, plan);
            fail("expected the plan to fail");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Not a file"));
        }

        assertFalse("steps after a failure must not run", Files.exists(root.resolve("after.txt")));
    }

    @Test
    public void refusesAPlanThatCouldOutlastTheResponseBudget() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(root.toString()))
                .maxSleepSeconds(600)
                .maxPlanSeconds(60)
                .build();

        List<AgentStep> plan = Arrays.asList(step("sleep", "seconds", "40"), step("sleep", "seconds", "40"));

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy).run(plan, StepTestSupport.log());
            fail("expected the plan to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("over the limit of 60s"));
        }
    }

    @Test
    public void refusesAnEmptyPlan() throws Exception {
        try {
            run(folder.getRoot().toPath().toRealPath(), Collections.emptyList());
            fail("expected an empty plan to be refused");
        } catch (StepValidationException e) {
            assertEquals("The plan has no steps", e.getMessage());
        }
    }

    @Test
    public void aZeroSleepIsSkippedRatherThanRefused() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();

        StepTestSupport.CollectingLog log = run(root, Collections.singletonList(step("sleep", "seconds", "0")));

        assertTrue(log.toString(), log.hasLineContaining("skipped"));
    }

    private StepTestSupport.CollectingLog run(Path aRoot, List<AgentStep> aSteps)
            throws StepValidationException, StepExecutionException {
        StepTestSupport.CollectingLog log = StepTestSupport.log();
        new StepPlanExecutor(StepRegistry.defaults(), StepTestSupport.policyFor(aRoot)).run(aSteps, log);
        return log;
    }
}
