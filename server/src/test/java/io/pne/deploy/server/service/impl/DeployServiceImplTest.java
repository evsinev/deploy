package io.pne.deploy.server.service.impl;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.server.agent.impl.AgentFinderServiceImpl;
import io.pne.deploy.server.agent.impl.LocalAgentServiceImpl;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.TaskParameters;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.pne.deploy.agent.api.command.AgentCommandType.SHELL;
import static io.pne.deploy.server.api.task.AgentFinder.agentByName;
import static io.pne.deploy.server.api.task.TaskId.generateTaskId;
import static java.util.Collections.singletonList;

public class DeployServiceImplTest {

    private static final Logger LOG = LoggerFactory.getLogger(DeployServiceImplTest.class);

    @Test
    public void runTask() throws Exception {
        LOG.debug("Running test...");
        CountDownLatch latch = new CountDownLatch(1);

        LocalAgentServiceImpl localAgentService = new LocalAgentServiceImpl((aId, aText) -> {
            System.out.println(aId + ": " + aText);
            latch.countDown();
        });
        AgentFinderServiceImpl agentFinderService = new AgentFinderServiceImpl(localAgentService);
        File aliasesDir = new File("src/test/resources/aliases");
        IDeployService service = new DeployServiceImpl(agentFinderService, aliasesDir);

        service.runTask(new Task(generateTaskId(), new TaskParameters(), singletonList(
                new TaskCommand(agentByName("localhost"), new AgentCommand(
                        new AgentCommandParameters(), SHELL, "echo", singletonList("test")
                ))))
        );

        System.out.println("Waiting for a text message from command ...");
        latch.await(5, TimeUnit.SECONDS);
    }

}