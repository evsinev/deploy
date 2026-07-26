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

            // The agents' command output (echo "deployed") is retained in the agent-log buffer and
            // served by GET /agentlog (the buffer already holds it by the time the deploy is DONE).
            String agentLog = "";
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && !agentLog.contains("deployed")) {
                agentLog = env.httpGet("/deploy/dashboard/agentlog");
                if (!agentLog.contains("deployed")) { Thread.sleep(200); }
            }
            assertTrue("agent echo output should appear in the agent log, got: " + agentLog, agentLog.contains("deployed"));
            // queues + latency are merged into one 'delivery' fragment on the live SSE stream
            String frames = env.readDashboardEvents("event: delivery", 10_000);
            assertTrue("dashboard should stream a 'delivery' event, got: " + frames, frames.contains("event: delivery"));

            // Config screen lists env vars but masks secrets.
            String config = env.httpGet("/deploy/dashboard/config");
            assertTrue("config screen should list REDMINE_URL, got: " + config, config.contains("REDMINE_URL"));
            assertFalse("the telegram token must be masked, not shown: " + config, config.contains("test-token"));

            // Aliases screen lists the demo alias and renders its definition.
            assertTrue(env.httpGet("/deploy/dashboard/aliases").contains("deploy-demo"));
            assertTrue(env.httpGet("/deploy/dashboard/alias?name=deploy-demo").contains("echo"));
        }
    }
}
