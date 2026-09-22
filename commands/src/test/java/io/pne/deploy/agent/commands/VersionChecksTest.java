package io.pne.deploy.agent.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersionChecksTest {

    @Test
    public void comparesPartByPart() {
        assertTrue(VersionChecks.compareVersions("1.2.4", "1.2.3") > 0);
        assertTrue(VersionChecks.compareVersions("1.2.3", "1.2.4") < 0);
        assertEquals(0, VersionChecks.compareVersions("1.2.3", "1.2.3"));
    }

    @Test
    public void treatsTheUsualSeparatorsAlike() {
        assertEquals(0, VersionChecks.compareVersions("1.2.3-4", "1.2.3_4"));
        assertTrue(VersionChecks.compareVersions("1.2.3-5", "1.2.3-4") > 0);
    }

    @Test(expected = IllegalStateException.class)
    public void refusesToGuessWhenTheShapesDiffer() {
        VersionChecks.compareVersions("1.2.3.4", "1.2.3");
    }

    @Test
    public void namesTheDirectionOfTheComparison() {
        assertEquals(">", VersionChecks.sign(1));
        assertEquals("<", VersionChecks.sign(-1));
        assertEquals("=", VersionChecks.sign(0));
    }
}
