package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import io.pne.deploy.server.api.task.TaskParameters;
import org.junit.Test;

import static io.pne.deploy.agent.api.command.AgentCommandId.generateCommandId;
import static io.pne.deploy.agent.api.command.AgentCommandType.SHELL;
import static io.pne.deploy.server.api.task.AgentFinder.agentByName;
import static io.pne.deploy.server.api.task.TaskId.generateTaskId;
import static java.util.Collections.singletonList;

/**
 * Created by esinev on 02/09/17.
 */
public class ServerVertxApplicationTest {

    @Test
    public void run() throws TaskException {
        ServerVertxApplication application = new ServerVertxApplication(new TestServerListener());
        application.start();
        try {
            IDeployService deployService = application.getDeployService();

            deployService.runTask(new Task(generateTaskId(), new TaskParameters(), singletonList(
                    new TaskCommand(agentByName("localhost"), new AgentCommand(
                            generateCommandId(), new AgentCommandParameters(), SHELL, "echo", singletonList("test")
                    ))))
            );
        } finally {
            application.stop();
        }

    }
}