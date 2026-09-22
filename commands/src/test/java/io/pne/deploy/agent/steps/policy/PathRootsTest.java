package io.pne.deploy.agent.steps.policy;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathRootsTest {

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
