package io.pne.deploy.agent.steps.policy;

import io.pne.deploy.agent.steps.StepValidationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PathGuardTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void acceptsAPathInsideARoot() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = policyWithWriteRoot(root);

        Path checked = PathGuard.checkWritable(policy, "write-file", "path", root.resolve("app/version.txt").toString());

        assertEquals(root.resolve("app/version.txt"), checked);
    }

    @Test
    public void refusesAPathOutsideEveryRoot() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = policyWithWriteRoot(root.resolve("allowed"));

        try {
            PathGuard.checkWritable(policy, "write-file", "path", root.resolve("elsewhere/version.txt").toString());
            fail("expected the path to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the writable roots"));
        }
    }

    @Test
    public void refusesAPathThatClimbsOutWithDotDot() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = policyWithWriteRoot(root.resolve("allowed"));

        // '..' is collapsed before the roots are consulted, so climbing out lands outside them and is refused.
        try {
            PathGuard.checkWritable(policy, "write-file", "path", root + "/allowed/../elsewhere/version.txt");
            fail("expected the path to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the writable roots"));
        }
    }

    @Test
    public void collapsesDotDotThatStaysInsideARoot() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = policyWithWriteRoot(root);

        Path checked = PathGuard.checkWritable(policy, "write-file", "path", root + "/app/../app/version.txt");

        assertEquals(root.resolve("app/version.txt"), checked);
    }

    @Test
    public void refusesAPathThatLeavesARootThroughASymbolicLink() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.createSymbolicLink(allowed.resolve("escape"), outside);

        StepPolicy policy = policyWithWriteRoot(allowed);

        try {
            PathGuard.checkWritable(policy, "write-file", "path", allowed.resolve("escape/version.txt").toString());
            fail("expected the link to be seen through");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the writable roots"));
        }
    }

    @Test
    public void refusesAPathThatLeavesARootThroughALinkToSomethingNotCreatedYet() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        Path outside = root.resolve("outside/not-created-yet.txt");
        Files.createSymbolicLink(allowed.resolve("marker"), outside);

        StepPolicy policy = policyWithWriteRoot(allowed);

        try {
            PathGuard.checkWritable(policy, "write-file", "path", allowed.resolve("marker").toString());
            fail("a link to a path that does not exist yet must still be resolved");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the writable roots"));
        }
    }

    @Test
    public void followsALinkThatStaysInsideARoot() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        Path real    = Files.createDirectories(allowed.resolve("real"));
        Files.createSymbolicLink(allowed.resolve("link"), real);

        StepPolicy policy = policyWithWriteRoot(allowed);

        Path checked = PathGuard.checkWritable(policy, "write-file", "path",
                allowed.resolve("link/version.txt").toString());

        assertEquals(real.resolve("version.txt"), checked);
    }

    @Test
    public void resolvesARelativeLinkAgainstTheDirectoryThatHoldsIt() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        Path real    = Files.createDirectories(allowed.resolve("real/sub"));
        Files.createSymbolicLink(allowed.resolve("alias"), real);
        Files.createSymbolicLink(real.resolve("link"), Paths.get("../version.txt"));

        StepPolicy policy = policyWithWriteRoot(allowed);

        // Reached through 'alias', the link still points at the file next to its own directory.
        Path checked = PathGuard.checkWritable(policy, "write-file", "path",
                allowed.resolve("alias/link").toString());

        assertEquals(real.getParent().resolve("version.txt"), checked);
    }

    @Test
    public void refusesARelativePath() {
        try {
            PathGuard.canonicalize("write-file", "path", "relative/version.txt");
            fail("expected a relative path to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must be an absolute path"));
        }
    }

    @Test
    public void refusesDeletingTheTopOfARoot() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        Path allowed = Files.createDirectories(root.resolve("allowed"));
        StepPolicy policy = policyWithWriteRoot(allowed);

        try {
            PathGuard.checkDeletable(policy, "unpack", "to", allowed.toString());
            fail("expected deleting the root to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("may not be deleted"));
        }
    }

    private static StepPolicy policyWithWriteRoot(Path aRoot) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .build();
    }
}
