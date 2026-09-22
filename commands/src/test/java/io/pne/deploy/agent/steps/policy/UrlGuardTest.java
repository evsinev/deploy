package io.pne.deploy.agent.steps.policy;

import io.pne.deploy.agent.steps.StepValidationException;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UrlGuardTest {

    private final HostAllowList allowed = new HostAllowList(Collections.singletonList("artifacts.internal"));

    @Test
    public void acceptsAnAllowedHost() throws Exception {
        assertEquals("artifacts.internal",
                UrlGuard.check(allowed, "fetch", "url", "http://artifacts.internal/app?version=1.0").getHost());
    }

    @Test
    public void refusesAHostThatIsNotAllowed() {
        expectRefusal("http://elsewhere.internal/app", "is not allowed by the agent policy");
    }

    @Test
    public void refusesASchemeThatIsNotHttp() {
        expectRefusal("file:///etc/passwd", "must be an http or https URL");
    }

    @Test
    public void refusesWhenNoHostIsAllowedAtAll() {
        try {
            UrlGuard.check(new HostAllowList(Collections.emptyList()), "fetch", "url", "http://artifacts.internal/app");
            fail("expected the URL to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no hosts are allowed"));
        }
    }

    private void expectRefusal(String aUrl, String aExpectedMessage) {
        try {
            UrlGuard.check(allowed, "fetch", "url", aUrl);
            fail("expected the URL to be refused: " + aUrl);
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(aExpectedMessage));
        }
    }
}
