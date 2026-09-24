package io.pne.deploy.agent.steps.policy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostAllowListTest {

    @Test
    public void matchesHostNameRegardlessOfCase() {
        HostAllowList allowed = new HostAllowList(Collections.singletonList("artifacts.internal"));

        assertTrue(allowed.allows("artifacts.internal", -1));
        assertTrue(allowed.allows("ARTIFACTS.INTERNAL", 8080));
        assertFalse(allowed.allows("other.internal", -1));
    }

    @Test
    public void matchesHostAndPortWhenThePortIsPartOfTheEntry() {
        HostAllowList allowed = new HostAllowList(Collections.singletonList("status.internal:8080"));

        assertTrue(allowed.allows("status.internal", 8080));
        assertFalse("a different port is a different destination", allowed.allows("status.internal", 9090));
    }

    @Test
    public void matchesAddressesInsideARange() {
        HostAllowList allowed = new HostAllowList(Collections.singletonList("10.20.0.0/16"));

        assertTrue(allowed.allows("10.20.1.5", 8080));
        assertFalse(allowed.allows("10.21.1.5", 8080));
    }

    @Test
    public void doesNotResolveNamesAgainstARange() {
        HostAllowList allowed = new HostAllowList(Collections.singletonList("127.0.0.0/8"));

        assertFalse("a name must never be resolved to pass a range check", allowed.allows("localhost", -1));
    }

    @Test
    public void emptyListAllowsNothing() {
        HostAllowList allowed = new HostAllowList(Collections.emptyList());

        assertTrue(allowed.isEmpty());
        assertFalse(allowed.allows("artifacts.internal", -1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedRangeIsRejected() {
        new HostAllowList(Arrays.asList("not-an-address/16"));
    }
}
