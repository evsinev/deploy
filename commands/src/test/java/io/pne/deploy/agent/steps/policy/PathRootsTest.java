package io.pne.deploy.agent.steps.policy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathRootsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aRootReachedThroughALinkStillAllowsWhatIsUnderIt() throws Exception {
        // The paths being checked are resolved through links, so the roots have to be too, or a system where
        // a directory is itself a link would refuse everything inside it.
        Path base = folder.getRoot().toPath().toRealPath();
        Path real = Files.createDirectories(base.resolve("real/apps"));
        Files.createSymbolicLink(base.resolve("apps"), real);

        PathRoots roots = new PathRoots(Collections.singletonList(base.resolve("apps").toString()));

        assertTrue(roots.allows(real.resolve("one/version.txt")));
    }

    @Test
    public void aGlobRootReachedThroughALinkStillMatches() throws Exception {
        Path base = folder.getRoot().toPath().toRealPath();
        Path real = Files.createDirectories(base.resolve("real/apps"));
        Files.createSymbolicLink(base.resolve("apps"), real);

        PathRoots roots = new PathRoots(Collections.singletonList(base.resolve("apps") + "/*/staging/**"));

        assertTrue(roots.allows(real.resolve("one/staging/1.0")));
        assertFalse(roots.allows(real.resolve("one/other/1.0")));
    }

    @Test
    public void prefixRootAllowsItselfAndEverythingBelow() {
        PathRoots roots = new PathRoots(Collections.singletonList("/srv/apps"));

        assertTrue(roots.allows(Paths.get("/srv/apps")));
        assertTrue(roots.allows(Paths.get("/srv/apps/one/two")));
        assertFalse(roots.allows(Paths.get("/srv/other")));
        assertFalse(roots.allows(Paths.get("/srv")));
    }

    @Test
    public void prefixRootIsNotConfusedBySimilarNames() {
        PathRoots roots = new PathRoots(Collections.singletonList("/srv/apps"));

        assertFalse(roots.allows(Paths.get("/srv/apps-backup")));
    }

    @Test
    public void globRootMatchesOneNamePerStar() {
        PathRoots roots = new PathRoots(Collections.singletonList("/srv/*/staging/**"));

        assertTrue(roots.allows(Paths.get("/srv/one/staging/1.0")));
        assertTrue(roots.allows(Paths.get("/srv/one/staging/nested/1.0")));
        assertFalse(roots.allows(Paths.get("/srv/one/other/1.0")));
        assertFalse(roots.allows(Paths.get("/srv/one/staging")));
    }

    @Test
    public void deleteIsRefusedForTheTopOfARoot() {
        PathRoots roots = new PathRoots(Collections.singletonList("/srv/apps"));

        assertTrue(roots.allowsDelete(Paths.get("/srv/apps/one")));
        assertFalse("the root itself must survive", roots.allowsDelete(Paths.get("/srv/apps")));
    }

    @Test
    public void deleteIsRefusedNearTheFilesystemRoot() {
        PathRoots roots = new PathRoots(Collections.singletonList("/"));

        assertFalse(roots.allowsDelete(Paths.get("/srv")));
        assertTrue(roots.allowsDelete(Paths.get("/srv/apps")));
    }

    @Test
    public void emptyRootsAllowNothing() {
        PathRoots roots = new PathRoots(Collections.emptyList());

        assertTrue(roots.isEmpty());
        assertFalse(roots.allows(Paths.get("/srv/apps")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void relativeRootIsRejected() {
        new PathRoots(Arrays.asList("srv/apps"));
    }
}
