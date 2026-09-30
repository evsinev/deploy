package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepPlanExecutor;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.agent.steps.StepTestSupport;
import io.pne.deploy.agent.steps.StepValidationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CopyFileStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void replacesTheDestinationByDefault() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path source = Files.writeString(root.resolve("list-2"), "b.example\n");
        Path target = Files.writeString(root.resolve("list.txt"), "a.example\n");
        Object before = fileKey(target);

        run(root, step("copy-file", "from", source.toString(), "to", target.toString()));

        assertEquals("b.example\n", Files.readString(target));
        assertNotEquals("the destination is a new file", before, fileKey(target));
    }

    @Test
    public void writesIntoTheSameFileInPlace() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path source = Files.writeString(root.resolve("list-2"), "b.example\n");
        Path target = Files.writeString(root.resolve("list.txt"), "a.example\nlonger.example\n");
        Object before = fileKey(target);

        run(root, step("copy-file", "from", source.toString(), "to", target.toString(), "mode", "in-place"));

        assertEquals("the old contents are gone, longer ones included", "b.example\n", Files.readString(target));
        assertEquals("a reader holding the file sees the new contents", before, fileKey(target));
    }

    @Test
    public void keepsThePermissionsOfTheDestinationInPlace() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path source = Files.writeString(root.resolve("list-2"), "b.example\n");
        Path target = Files.writeString(root.resolve("list.txt"), "a.example\n");
        Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rw-r--r--"));
        Files.setPosixFilePermissions(target, ownerOnly);

        run(root, step("copy-file", "from", source.toString(), "to", target.toString(), "mode", "in-place"));

        assertEquals(ownerOnly, Files.getPosixFilePermissions(target));
    }

    @Test
    public void createsAMissingDestinationInPlace() throws Exception {
        Path root   = folder.getRoot().toPath().toRealPath();
        Path source = Files.writeString(root.resolve("list-2"), "b.example\n");
        Path target = root.resolve("list.txt");

        run(root, step("copy-file", "from", source.toString(), "to", target.toString(), "mode", "in-place"));

        assertEquals("b.example\n", Files.readString(target));
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    public void refusesAnUnknownMode() {
        try {
            StepRegistry.defaults().create(step("copy-file", "from", "/tmp/a", "to", "/tmp/b", "mode", "append"));
            fail("expected the mode to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must be one of [replace, in-place]"));
        }
    }

    private static Object fileKey(Path aPath) throws Exception {
        return Files.readAttributes(aPath, BasicFileAttributes.class).fileKey();
    }

    private static void run(Path aRoot, AgentStep aStep) throws StepValidationException, StepExecutionException {
        new StepPlanExecutor(StepRegistry.defaults(), StepTestSupport.policyFor(aRoot))
                .run(Collections.singletonList(aStep), StepTestSupport.log());
    }
}
