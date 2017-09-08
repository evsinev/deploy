package io.pne.deploy.server.vertx;

import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import org.junit.Test;

import java.io.File;

/**
 * Created by esinev on 02/09/17.
 */
public class VertxServerApplicationTest {

    @Test
    public void run() throws TaskException {
        VertxServerApplication application = new VertxServerApplication(new TestServerApplicationListener()
                , new IVertxServerConfiguration() {
            @Override
            public int getPort() {
                return 9090;
            }

            @Override
            public File getAliasesDir() {
                return new File("../server/src/test/resources/aliases");
            }
        });
        application.start();
        try {
            IDeployService deployService = application.getDeployService();

//            deployService.runTask(new Task(generateTaskId(), new TaskParameters(), singletonList(
//                    new TaskCommand(agentByName("localhost"), new AgentCommand(
//                            generateCommandId(), new AgentCommandParameters(), SHELL, "echo", singletonList("test")
//                    ))))
//            );
        } finally {
            application.stop();
        }

    }
}