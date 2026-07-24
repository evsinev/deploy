package io.pne.deploy.tests;

import io.pne.deploy.tests.env.LocalEnvironment;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end smoke test of the whole environment: boots the deploy-server, two websocket agents, and
 * Redmine/GitLab/Telegram HTTP mocks, then drives one Redmine issue through the full pipeline and
 * asserts every external system was exercised.
 */
public class FullEnvironmentTest {

    @Test
    public void deploysAnIssueThroughTheWholeEnvironment() throws Exception {
        try (LocalEnvironment env = new LocalEnvironment()) {
            env.start();
            env.triggerIssue(1001);

            // A DONE status PUT only happens after validation → Processing → runTask on BOTH agents → Done.
            assertTrue("Redmine should receive the DONE status update (whole chain succeeded)",
                    env.redmine().await(r -> "PUT".equals(r.method) && r.body.contains("Task is DONE"), 25_000));
            assertTrue("GitLab compare (diff) should be requested",
                    env.gitlab().await(r -> "GET".equals(r.method) && r.path.contains("/repository/compare"), 25_000));
            assertTrue("Telegram sendMessage should be delivered",
                    env.telegram().await(r -> "POST".equals(r.method) && r.path.contains("/sendMessage"), 25_000));

            // The dashboard streams the agents' command output (echo) as an SSE 'logs' card.
            String frames = env.readDashboardEvents("deployed", 10_000);
            assertTrue("dashboard should stream an 'logs' event, got: " + frames, frames.contains("event: logs"));
            assertTrue("agent echo output should appear in the logs card, got: " + frames, frames.contains("deployed"));

            // Config screen lists env vars but masks secrets.
            String config = env.httpGet("/deploy/dashboard/config");
            assertTrue("config screen should list REDMINE_URL, got: " + config, config.contains("REDMINE_URL"));
            assertFalse("the telegram token must be masked, not shown: " + config, config.contains("test-token"));

            // Aliases screen lists the demo alias and renders its definition.
            assertTrue(env.httpGet("/deploy/dashboard/aliases").contains("deploy-demo"));
            assertTrue(env.httpGet("/deploy/dashboard/aliases/deploy-demo").contains("echo"));
        }
    }
}
