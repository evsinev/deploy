package io.pne.deploy.tests;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.agent.websocket.WebSocketAgentApplication;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.vertx.IVertxServerConfiguration;
import io.pne.deploy.server.vertx.VertxServerApplication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A plan built on the server, sent to a real agent over the websocket, and carried out there.
 *
 * <p>The agent only accepts the plan because a policy file on its side allows those directories, which is the
 * whole point of the arrangement: the server says what to do, the host says what may be done to it.
 */
public class StepPlanEndToEndTest {

    private static final int PORT = 9096;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aPlanFromAnAliasIsCarriedOutByTheAgent() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path deployedTo = Files.createDirectories(root.resolve("deployed"));
        Path aliases    = writeAliases(root);
        Path policy     = writePolicy(root, deployedTo);

        run(aliases, policy, app -> {
            Task task = app.getDeployService().parseAlias("write-version 1.2.3", 123);
            app.getDeployService().runTask(task);
        });

        assertEquals("1.2.3" + System.lineSeparator(),
                Files.readString(deployedTo.resolve("version.txt"), StandardCharsets.UTF_8));
    }

    @Test
    public void aPlanTheHostDoesNotAllowIsRefusedAndChangesNothing() throws Exception {
        Path root       = folder.getRoot().toPath().toRealPath();
        Path deployedTo = Files.createDirectories(root.resolve("deployed"));
        Path offLimits  = Files.createDirectories(root.resolve("off-limits"));
        Path aliases    = writeAliases(root);
        Path policy     = writePolicy(root, deployedTo);

        try {
            run(aliases, policy, app -> {
                Task task = app.getDeployService().parseAlias("write-elsewhere 1.2.3", 123);
                app.getDeployService().runTask(task);
            });
            fail("expected the agent to refuse a plan outside what its policy allows");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not inside the writable roots"));
        }

        assertTrue("nothing may be written outside the allowed directory",
                isEmpty(offLimits));
    }

    private interface ServerAction {
        void run(VertxServerApplication aApplication) throws Exception;
    }

    private void run(Path aAliases, Path aPolicy, ServerAction aAction) throws Exception {
        TestServerApplicationListener serverListener = new TestServerApplicationListener();
        VertxServerApplication server = new VertxServerApplication(serverListener, new IVertxServerConfiguration() {
            @Override
            public int getPort() {
                return PORT;
            }

            @Override
            public File getAliasesDir() {
                return aAliases.toFile();
            }

            @Override
            public File getRecipesDir() {
                return aAliases.resolveSibling("recipes").toFile();
            }
        }, StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class));

        server.start();
        assertTrue("server did not start in time", serverListener.waitUntilStarted(5, TimeUnit.SECONDS));

        TestAgentApplicationListener agentListener = new TestAgentApplicationListener();
        WebSocketAgentApplication agent = new WebSocketAgentApplication(agentListener,
                new TestAgentStartupParameters("http://127.0.0.1:" + PORT + "/", "test-host", aPolicy.toFile()));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(agent::start);
        assertTrue("agent did not connect in time", agentListener.waitUntilConnected(5, TimeUnit.SECONDS));

        try {
            aAction.run(server);
        } finally {
            agent.stop();
            executor.shutdownNow();
            server.stop();
        }
    }

    private Path writeAliases(Path aRoot) throws Exception {
        Path aliases = Files.createDirectories(aRoot.resolve("aliases"));
        Files.createDirectories(aRoot.resolve("recipes"));

        Files.writeString(aliases.resolve("write-version.yml"),
                  "params:\n"
                + "  - { name: version, type: version }\n"
                + "commands:\n"
                + "  - agents: test-host\n"
                + "    steps:\n"
                + "      - type: write-file\n"
                + "        params: { path: " + aRoot.resolve("deployed/version.txt") + ", content: \"${version}\" }\n",
                StandardCharsets.UTF_8);

        Files.writeString(aliases.resolve("write-elsewhere.yml"),
                  "params:\n"
                + "  - { name: version, type: version }\n"
                + "commands:\n"
                + "  - agents: test-host\n"
                + "    steps:\n"
                + "      - type: write-file\n"
                + "        params: { path: " + aRoot.resolve("off-limits/version.txt") + ", content: \"${version}\" }\n",
                StandardCharsets.UTF_8);

        return aliases;
    }

    private Path writePolicy(Path aRoot, Path aWritable) throws Exception {
        Path policy = aRoot.resolve("policy.yml");
        Files.writeString(policy,
                  "allowShell: true\n"
                + "writeRoots:\n"
                + "  - " + aWritable + "\n",
                StandardCharsets.UTF_8);
        return policy;
    }

    private static boolean isEmpty(Path aDirectory) throws Exception {
        try (var entries = Files.list(aDirectory)) {
            return entries.findAny().isEmpty();
        }
    }
}
