package io.pne.deploy.tests;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.websocket.WebSocketAgentApplication;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.pne.deploy.agent.api.command.AgentCommandType.SHELL;
import static io.pne.deploy.server.api.task.AgentFinder.agentByName;
import static io.pne.deploy.server.api.task.TaskId.generateTaskId;
import static java.util.Collections.singletonList;

public class VertxAndWebsocketTest {

    private static final Logger LOG = LoggerFactory.getLogger(VertxAndWebsocketTest.class);

    @Test
    public void test() throws TaskException, InterruptedException {
        Task task = new Task(generateTaskId(), new TaskParameters(), Arrays.asList(
                new TaskCommand(agentByName("test-host"), new AgentCommand(
                         new AgentCommandParameters(), SHELL, "echo", singletonList("test123")
                ))
                , new TaskCommand(agentByName("test-host"), new AgentCommand(
                         new AgentCommandParameters(), SHELL, "echo", singletonList("test1234")
                ))
        )
        );
        runTask(task);

    }

    private void runTask(Task aTask) throws TaskException, InterruptedException {
        TestServerApplicationListener serverListener = new TestServerApplicationListener();
        VertxServerApplication server = new VertxServerApplication(serverListener, new IVertxServerConfiguration() {
            @Override
            public int getPort() {
                return 8080;
            }

            @Override
            public File getAliasesDir() {
                return new File("../server/src/test/resources/aliases");
            }
        });
        try {
            server.start();
            serverListener.waitUntilStarted(5, TimeUnit.SECONDS);

            TestAgentApplicationListener agentListener = new TestAgentApplicationListener();
            TestAgentStartupParameters agentParameters = new TestAgentStartupParameters("http://127.0.0.1:8080/", "test-host");
            WebSocketAgentApplication agent = new WebSocketAgentApplication(agentListener, agentParameters);
            try {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(agent::start);

                agentListener.waitUntilConnected(5, TimeUnit.SECONDS);

                LOG.debug("Sending task...");
                server.getDeployService().runTask(aTask);

            } finally {
                agent.stop();
            }
        } finally {
            server.stop();
        }
    }
}
