package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepPlanExecutor;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.agent.steps.StepTestSupport;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SignalServiceStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void runsTheControlProgramWithAFixedArgumentList() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));
        Path recorded   = root.resolve("recorded-arguments.txt");
        Path control    = controlProgram(root, recorded);

        StepTestSupport.CollectingLog log = StepTestSupport.log();
        new StepPlanExecutor(StepRegistry.defaults(), policy(root, control)).run(
                Collections.singletonList(step("signal-service", "service", serviceDir.toString())), log);

        assertEquals("-h " + serviceDir + System.lineSeparator(),
                Files.readString(recorded, StandardCharsets.UTF_8));
        assertTrue(log.toString(), log.hasLineContaining("running " + control));
    }

    @Test
    public void refusesADirectoryThePolicyDoesNotName() throws Exception {
        Path root     = folder.getRoot().toPath().toRealPath();
        Path outside  = Files.createDirectories(root.resolve("elsewhere/demo"));
        Path control  = controlProgram(root, root.resolve("recorded-arguments.txt"));

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy(root, control)).run(
                    Collections.singletonList(step("signal-service", "service", outside.toString())),
                    StepTestSupport.log());
            fail("expected the directory to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not one of the service directories"));
        }
    }

    @Test
    public void reportsAFailingControlProgram() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));
        Path control    = root.resolve("control");
        Files.writeString(control, "#!/bin/sh\necho cannot control\nexit 3\n", StandardCharsets.UTF_8);
        control.toFile().setExecutable(true);

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy(root, control)).run(
                    Collections.singletonList(step("signal-service", "service", serviceDir.toString())),
                    StepTestSupport.log());
            fail("expected a non-zero exit to fail the step");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("returned 3"));
        }
    }

    @Test
    public void refusesASignalThatIsNotEnabled() {
        try {
            StepRegistry.defaults().create(step("signal-service", "service", "/service/demo", "signal", "down"));
            fail("expected the signal to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not enabled"));
        }
    }

    @Test
    public void refusesAMissingControlProgram() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy(root, root.resolve("no-such-program"))).run(
                    Collections.singletonList(step("signal-service", "service", serviceDir.toString())),
                    StepTestSupport.log());
            fail("expected the missing program to be reported before running anything");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing or not executable"));
        }
    }

    private static Path controlProgram(Path aRoot, Path aRecorded) throws Exception {
        Path control = aRoot.resolve("control");
        Files.writeString(control, "#!/bin/sh\necho \"$@\" > " + aRecorded + "\n", StandardCharsets.UTF_8);
        control.toFile().setExecutable(true);
        return control;
    }

    private static StepPolicy policy(Path aRoot, Path aControl) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .serviceDirs(Collections.singletonList(aRoot.resolve("service").toString()))
                .serviceControlBinary(aControl)
                .build();
    }
}
