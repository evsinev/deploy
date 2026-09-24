package io.pne.deploy.agent.service.policy;

import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.junit.Test;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PolicyLoaderTest {

    @Test
    public void readsEverySetting() {
        StepPolicy policy = PolicyLoader.parse(new StringReader(
                  "allowShell: false\n"
                + "shellAllowedPrefix: ./bin/\n"
                + "fetchHosts:  [ artifacts.internal ]\n"
                + "statusHosts: [ 10.20.0.0/16 ]\n"
                + "writeRoots:  [ /srv/apps ]\n"
                + "readRoots:   [ /srv/apps, /srv/shared ]\n"
                + "serviceDirs: [ /service ]\n"
                + "serviceControl: program\n"
                + "serviceControlBinary: /usr/local/bin/control\n"
                + "limits:\n"
                + "  maxFetchBytes: 1024\n"
                + "  maxPlanSeconds: 120\n"));

        assertFalse(policy.isShellAllowed());
        assertEquals("./bin/", policy.getShellAllowedPrefix());
        assertTrue(policy.getFetchHosts().allows("artifacts.internal", -1));
        assertTrue(policy.getStatusHosts().allows("10.20.1.1", 8080));
        assertTrue(policy.getWriteRoots().allows(Paths.get("/srv/apps/one")));
        assertTrue(policy.getReadRoots().allows(Paths.get("/srv/shared/one")));
        assertTrue(policy.getServiceDirs().allows(Paths.get("/service/demo")));
        assertTrue(policy.isServiceControlledByProgram());
        assertEquals(Paths.get("/usr/local/bin/control"), policy.getServiceControlBinary());
        assertEquals(1024, policy.getMaxFetchBytes());
        assertEquals(120, policy.getMaxPlanSeconds());
    }

    @Test
    public void aServiceIsControlledThroughTheSupervisorUnlessAskedOtherwise() {
        StepPolicy policy = PolicyLoader.parse(new StringReader("serviceDirs: [ /service ]\n"));

        assertFalse("nothing is executed by default", policy.isServiceControlledByProgram());
    }

    @Test
    public void refusesAServiceControlItDoesNotKnow() {
        try {
            PolicyLoader.parse(new StringReader("serviceControl: magic\n"));
            fail("expected the setting to be refused");
        } catch (IllegalArgumentException | IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("serviceControl"));
        }
    }

    @Test
    public void statusHostsFallBackToTheFetchHosts() {
        StepPolicy policy = PolicyLoader.parse(new StringReader("fetchHosts: [ artifacts.internal ]\n"));

        assertTrue(policy.getStatusHosts().allows("artifacts.internal", -1));
    }

    @Test
    public void reportsAMisspelledSetting() {
        try {
            PolicyLoader.parse(new StringReader("writeRoot: [ /srv/apps ]\n"));
            fail("expected the typo to be reported");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Unknown policy setting(s) [writeRoot]"));
        }
    }

    @Test
    public void aMissingFileMeansStepPlansAreRefused() {
        assertNull(PolicyLoader.loadOrNull(new File("no-such-policy.yml")));
    }
}
