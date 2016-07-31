package io.pne.deploy.test;

import io.pne.deploy.agent.AgentApplication;
import io.pne.deploy.client.ClientApplication;
import io.pne.deploy.client.ClientParameters;
import io.pne.deploy.server.ServerApplication;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SendCommandFromClientTest {

    private static final Logger LOG = LoggerFactory.getLogger(SendCommandFromClientTest.class);

    Executor executor = Executors.newCachedThreadPool();

    @Test
    @Ignore
    public void test() throws InterruptedException, IOException {
        TestServerListener serverListener = new TestServerListener();
        ServerApplication server = new ServerApplication(serverListener);
        executor.execute(server::start);

        serverListener.awaitStarted();

        TestAgentListener agentListener = new TestAgentListener();
        AgentApplication agent = new AgentApplication(agentListener);
        executor.execute(agent::start);

        agentListener.awaitConnected();

        ClientParameters clientParameters = new ClientParameters();
        clientParameters.server = "http://localhost:9020/deploy";
        clientParameters.issue  = "123";
        clientParameters.command = "@script name=test.sh host=127.0.0.1 KEY_1=VALUE_1";

        ClientApplication client = new ClientApplication(clientParameters);
        client.runCommand();

    }
}
