package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * How long the server waits for an agent.
 *
 * <p>A deployment that waits for a service to come back can outlast any fixed wait, so the wait follows the
 * agent: as long as it keeps reporting, the server keeps waiting; once it goes quiet, the server gives up.
 */
public class CommandResponsesTest {

    @Test
    public void returnsTheAnswerAsSoonAsItArrives() throws Exception {
        CommandResponses responses = new CommandResponses(5, 10);
        ScheduledExecutorService later = Executors.newSingleThreadScheduledExecutor();
        later.schedule(() -> responses.addCommandResponse("cmd-1", new RunAgentCommandResponse("cmd-1")),
                200, TimeUnit.MILLISECONDS);

        try {
            assertNotNull(responses.awaitForCommandResponse("cmd-1"));
        } finally {
            later.shutdownNow();
        }
    }

    @Test
    public void givesUpOnAnAgentThatHasGoneQuiet() throws Exception {
        CommandResponses responses = new CommandResponses(1, 30);

        try {
            responses.awaitForCommandResponse("cmd-1");
            fail("expected the server to give up on a silent agent");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nothing heard"));
        }
    }

    @Test
    public void keepsWaitingWhileTheAgentIsStillReporting() throws Exception {
        CommandResponses responses = new CommandResponses(2, 30);

        ScheduledExecutorService reporting = Executors.newSingleThreadScheduledExecutor();
        reporting.scheduleAtFixedRate(() -> responses.noteActivity("cmd-1"), 0, 300, TimeUnit.MILLISECONDS);
        reporting.schedule(() -> responses.addCommandResponse("cmd-1", new RunAgentCommandResponse("cmd-1")),
                4, TimeUnit.SECONDS);

        try {
            long started = System.currentTimeMillis();
            assertNotNull(responses.awaitForCommandResponse("cmd-1"));
            assertTrue("it must have waited past the quiet limit because the agent kept reporting",
                    System.currentTimeMillis() - started > 3_000);
        } finally {
            reporting.shutdownNow();
        }
    }

    @Test
    public void stopsEvenAnAgentThatNeverStopsReporting() throws Exception {
        CommandResponses responses = new CommandResponses(30, 2);

        ScheduledExecutorService reporting = Executors.newSingleThreadScheduledExecutor();
        reporting.scheduleAtFixedRate(() -> responses.noteActivity("cmd-1"), 0, 200, TimeUnit.MILLISECONDS);

        try {
            responses.awaitForCommandResponse("cmd-1");
            fail("expected an overall limit to apply");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("longer than any deployment should take"));
        } finally {
            reporting.shutdownNow();
        }
    }
}
