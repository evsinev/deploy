package io.pne.deploy.tests;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.agent.websocket.WebSocketAgentApplication;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.TaskParameters;
import io.pne.deploy.server.vertx.IVertxServerConfiguration;
import io.pne.deploy.server.vertx.VertxServerApplication;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.pne.deploy.agent.api.command.AgentCommandType.SHELL;
import static io.pne.deploy.server.api.task.AgentFinder.agentByName;
import static io.pne.deploy.server.api.task.TaskId.generateTaskId;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VertxAndWebsocketTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxAndWebsocketTest.class);

    @Test
    public void twoEchoCommandsBothSucceed() throws Exception {
        Task task = new Task(generateTaskId(), new TaskParameters(), Arrays.asList(
                shell("test-host", "echo", "test123"),
                shell("test-host", "echo", "test1234")
        ), "test-task", 0);

        List<RunAgentCommandResponse> responses = runAndCollect(9090, "test-host", 2,
                app -> app.getDeployService().runTask(task));

        assertEquals(2, responses.size());
        assertAllSucceeded(responses);
    }

    @Test
    public void failingCommandThrowsTaskException() throws Exception {
        Task task = new Task(generateTaskId(), new TaskParameters(), singletonList(
                new TaskCommand(agentByName("test-host"), new AgentCommand(
                        new AgentCommandParameters(), SHELL, "sh", Arrays.asList("-c", "exit 1")))
        ), "failing-task", 0);

        try {
            runAndCollect(9091, "test-host", 1, app -> app.getDeployService().runTask(task));
            fail("expected TaskException for a non-zero command");
        } catch (TaskException expected) {
            LOG.debug("got expected TaskException: {}", expected.getMessage());
        }
    }

    @Test
    public void aliasDrivenTaskRunsViaLocalAgent() throws Exception {
        // proc.yml routes to the built-in "localhost" agent, which the server executes in-process
        // (no websocket round-trip), so success is proven by runTask NOT throwing TaskException.
        List<RunAgentCommandResponse> responses = runAndCollect(9092, null, 0, app -> {
            Task task = app.getDeployService().parseAlias("proc hello", 123);
            app.getDeployService().runTask(task);
        });

        assertTrue("localhost runs in-process, so no websocket responses reach the listener",
                responses.isEmpty());
    }

    // --- harness ---

    private interface ServerAction {
        void run(VertxServerApplication app) throws Exception;
    }

    /**
     * Boots a Vertx server (and, when {@code agentName} is non-null, a websocket agent) in-process, runs the
     * action, then collects the command responses the server received BEFORE tearing everything down (delivery
     * to the listener is async on the vert.x event loop, so we must wait while the server is still up).
     * Note: the built-in "localhost" agent id is executed in-process by the server and produces no websocket
     * responses, so pass {@code agentName == null} for localhost-routed tasks.
     */
    private List<RunAgentCommandResponse> runAndCollect(int port, String agentName, int expected, ServerAction action) throws Exception {
        TestServerApplicationListener serverListener = new TestServerApplicationListener();
        VertxServerApplication server = new VertxServerApplication(serverListener, new IVertxServerConfiguration() {
            @Override
            public int getPort() {
                return port;
            }

            @Override
            public File getAliasesDir() {
                return new File("../server/src/test/resources/aliases");
            }
        }, StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class));
        try {
            server.start();
            assertTrue("server did not start in time", serverListener.waitUntilStarted(5, TimeUnit.SECONDS));

            WebSocketAgentApplication agent = null;
            ExecutorService executor = null;
            if (agentName != null) {
                TestAgentApplicationListener agentListener = new TestAgentApplicationListener();
                agent = new WebSocketAgentApplication(agentListener,
                        new TestAgentStartupParameters("http://127.0.0.1:" + port + "/", agentName));
                executor = Executors.newSingleThreadExecutor();
                executor.execute(agent::start);
                assertTrue("agent did not connect in time", agentListener.waitUntilConnected(5, TimeUnit.SECONDS));
            }
            try {
                LOG.debug("Running action against {} ...", agentName);
                action.run(server);
                return serverListener.awaitResponses(expected, 5, TimeUnit.SECONDS);
            } finally {
                if (agent != null) {
                    agent.stop();
                }
                if (executor != null) {
                    executor.shutdownNow();
                }
            }
        } finally {
            server.stop();
        }
    }

    private static void assertAllSucceeded(List<RunAgentCommandResponse> responses) {
        for (RunAgentCommandResponse response : responses) {
            assertNull("command failed: " + response, response.error);
        }
    }

    private static TaskCommand shell(String agent, String name, String arg) {
        return new TaskCommand(agentByName(agent),
                new AgentCommand(new AgentCommandParameters(), SHELL, name, singletonList(arg)));
    }
}
