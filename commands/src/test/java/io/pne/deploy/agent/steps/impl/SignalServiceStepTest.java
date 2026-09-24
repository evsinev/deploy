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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SignalServiceStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    // --- the default: write to the supervisor's control channel, start nothing ---

    @Test(timeout = 20_000)
    public void asksTheSupervisorToReloadWithoutStartingAnything() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = supervisedService(root, "demo");
        BlockingQueue<Integer> read = new ArrayBlockingQueue<>(4);

        Thread supervisor = readingFrom(SuperviseControl.controlFile(serviceDir), read);
        try {
            run(policy(root), step("signal-service", "service", serviceDir.toString()));

            assertEquals((Integer) (int) 'h', read.poll(10, TimeUnit.SECONDS));
        } finally {
            supervisor.interrupt();
        }
    }

    @Test(timeout = 20_000)
    public void canAskForATerminationInstead() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = supervisedService(root, "demo");
        BlockingQueue<Integer> read = new ArrayBlockingQueue<>(4);

        Thread supervisor = readingFrom(SuperviseControl.controlFile(serviceDir), read);
        try {
            run(policy(root), step("signal-service", "service", serviceDir.toString(), "signal", "term"));

            assertEquals((Integer) (int) 't', read.poll(10, TimeUnit.SECONDS));
        } finally {
            supervisor.interrupt();
        }
    }

    @Test
    public void refusesADirectoryNoSupervisorManages() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));

        try {
            run(policy(root), step("signal-service", "service", serviceDir.toString()));
            fail("expected the directory to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not a service a supervisor is managing"));
        }
    }

    @Test(timeout = 30_000)
    public void reportsASupervisorThatIsNotRunning() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = supervisedService(root, "demo");

        // The channel is there but nothing is reading it, which is a supervisor that has stopped.
        try {
            run(policy(root), step("signal-service", "service", serviceDir.toString()));
            fail("expected the missing supervisor to be reported");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No supervisor is reading"));
        }
    }

    @Test
    public void refusesADirectoryThePolicyDoesNotName() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path outside = Files.createDirectories(root.resolve("elsewhere/demo"));

        try {
            run(policy(root), step("signal-service", "service", outside.toString()));
            fail("expected the directory to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not one of the service directories"));
        }
    }

    @Test
    public void refusesASignalItDoesNotKnow() {
        try {
            StepRegistry.defaults().create(step("signal-service", "service", "/service/demo", "signal", "down"));
            fail("expected the signal to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must be one of [hup, term]"));
        }
    }

    // --- the other way: run a control program, for a supervisor that does not use a control channel ---

    @Test
    public void runsTheControlProgramWithAFixedArgumentList() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));
        Path recorded   = root.resolve("recorded-arguments.txt");
        Path control    = controlProgram(root, recorded);

        run(programPolicy(root, control), step("signal-service", "service", serviceDir.toString()));

        assertEquals("-h " + serviceDir + System.lineSeparator(),
                Files.readString(recorded, StandardCharsets.UTF_8));
    }

    @Test
    public void reportsAFailingControlProgram() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));
        Path control    = root.resolve("control");
        Files.writeString(control, "#!/bin/sh\necho cannot control\nexit 3\n", StandardCharsets.UTF_8);
        control.toFile().setExecutable(true);

        try {
            run(programPolicy(root, control), step("signal-service", "service", serviceDir.toString()));
            fail("expected a non-zero exit to fail the step");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("returned 3"));
        }
    }

    @Test
    public void refusesAMissingControlProgram() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path serviceDir = Files.createDirectories(root.resolve("service/demo"));

        try {
            run(programPolicy(root, root.resolve("no-such-program")),
                    step("signal-service", "service", serviceDir.toString()));
            fail("expected the missing program to be reported before running anything");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing or not executable"));
        }
    }

    // --- helpers ---

    private static void run(StepPolicy aPolicy, io.pne.deploy.agent.api.command.AgentStep aStep)
            throws StepValidationException, StepExecutionException {
        new StepPlanExecutor(StepRegistry.defaults(), aPolicy)
                .run(Collections.singletonList(aStep), StepTestSupport.log());
    }

    private static StepPolicy policy(Path aRoot) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .serviceDirs(Collections.singletonList(aRoot.resolve("service").toString()))
                .build();
    }

    private static StepPolicy programPolicy(Path aRoot, Path aControl) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .serviceDirs(Collections.singletonList(aRoot.resolve("service").toString()))
                .serviceControl(StepPolicy.SERVICE_CONTROL_PROGRAM)
                .serviceControlBinary(aControl)
                .build();
    }

    /** A service directory with the named pipe a supervisor would be reading. */
    private static Path supervisedService(Path aRoot, String aName) throws Exception {
        Path serviceDir = Files.createDirectories(aRoot.resolve("service").resolve(aName));
        Files.createDirectories(serviceDir.resolve("supervise"));

        Process mkfifo = new ProcessBuilder("mkfifo", SuperviseControl.controlFile(serviceDir).toString()).start();
        assertEquals("mkfifo", 0, mkfifo.waitFor());
        return serviceDir;
    }

    private static Path controlProgram(Path aRoot, Path aRecorded) throws Exception {
        Path control = aRoot.resolve("control");
        Files.writeString(control, "#!/bin/sh\necho \"$@\" > " + aRecorded + "\n", StandardCharsets.UTF_8);
        control.toFile().setExecutable(true);
        return control;
    }

    private static Thread readingFrom(Path aControl, BlockingQueue<Integer> aRead) {
        Thread thread = new Thread(() -> {
            try (InputStream in = Files.newInputStream(aControl)) {
                int value;
                while ((value = in.read()) >= 0) {
                    aRead.add(value);
                }
            } catch (IOException e) {
                // the pipe went away with the test
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
