package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.StepContext;
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
import java.util.stream.Stream;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WriteFileStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesTheVersionMarkerWithATrailingNewline() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path target = root.resolve("app/version.txt");

        run(root, step("write-file", "path", target.toString(), "content", "1.2.3", "mkdirs", "true"));

        assertEquals("1.2.3" + System.lineSeparator(), Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void writesWithoutATrailingNewlineWhenAsked() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path target = root.resolve("version.txt");

        run(root, step("write-file", "path", target.toString(), "content", "1.2.3", "newline", "false"));

        assertEquals("1.2.3", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void leavesNoTemporaryFileBehind() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path target = root.resolve("version.txt");

        run(root, step("write-file", "path", target.toString(), "content", "1.2.3"));

        try (Stream<Path> files = Files.list(root)) {
            assertEquals(Collections.singletonList(target), files.sorted().toList());
        }
    }

    @Test
    public void refusesToWriteOutsideTheAllowedRoots() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(allowed.toString()))
                .build();

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy).run(
                    Collections.singletonList(step("write-file",
                            "path", root.resolve("elsewhere.txt").toString(), "content", "x")),
                    StepTestSupport.log());
            fail("expected the plan to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the writable roots"));
        }
    }

    @Test
    public void reportsAnUnknownParameter() {
        try {
            StepRegistry.defaults().create(step("write-file", "path", "/tmp/x", "contents", "typo"));
            fail("expected the typo to be reported");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unknown parameter(s) [contents]"));
        }
    }

    private static void run(Path aRoot, io.pne.deploy.agent.api.command.AgentStep aStep)
            throws StepValidationException, StepExecutionException {
        StepPolicy policy = StepTestSupport.policyFor(aRoot);
        new StepPlanExecutor(StepRegistry.defaults(), policy)
                .run(Collections.singletonList(aStep), StepTestSupport.log());
    }
}
