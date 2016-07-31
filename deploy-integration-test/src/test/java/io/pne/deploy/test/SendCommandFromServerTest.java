package io.pne.deploy.test;

import io.pne.deploy.agent.AgentApplication;
import io.pne.deploy.api.IClientMessage;
import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.messages.HeartbeatAck;
import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptLog;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import io.pne.deploy.server.ServerApplication;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SendCommandFromServerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SendCommandFromServerTest.class);

    Executor executor = Executors.newCachedThreadPool();

    @Test
    public void test() throws InterruptedException {
        TestServerListener serverListener = new TestServerListener();
        ServerApplication server = new ServerApplication(serverListener);
        executor.execute(server::start);

        serverListener.awaitStarted();

        TestAgentListener agentListener = new TestAgentListener();
        AgentApplication agent = new AgentApplication(agentListener);
        executor.execute(agent::start);

        agentListener.awaitConnected();

        Heartbeat heartbeat = agentListener.awaitMessage();
        Assert.assertNotNull(heartbeat);

        HeartbeatAck heartbeatAck = serverListener.awaitMessage();
        Assert.assertNotNull(heartbeatAck);

        ImmutableShellScriptParameters shell = ImmutableShellScriptParameters.builder()
                .filename("test.sh")
                .group("../deploy-agent/src/test/resources/scripts")
                .taskId(UUID.randomUUID().toString())
                .username(ShellScriptParameters.USERNAME_NON_ROOT)
                .build();

        server.sendMessage("127.0.0.1", shell);

        ShellScriptParameters agentShell = agentListener.awaitMessage();
        Assert.assertEquals(shell.filename(), agentShell.filename());

        do {
            IClientMessage message = serverListener.awaitMessage();
            if(message instanceof ShellScriptResult) {
                ShellScriptResult result = (ShellScriptResult) message;
                Assert.assertEquals(0, result.exitCode());
                break;
            } else if(message instanceof ShellScriptLog) {
                ShellScriptLog log = (ShellScriptLog) message;
                LOG.info("Got log message: {}", log);
            } else {
                throw new IllegalStateException("Wrong message type: "+ message);
            }

        } while (true);

    }
}
