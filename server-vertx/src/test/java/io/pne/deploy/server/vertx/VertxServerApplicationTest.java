package io.pne.deploy.server.vertx;

import io.pne.deploy.client.redmine.remote.impl.ImmutableIRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.impl.RedmineRemoveConfigBuilder;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;

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
        }, createTestConfig());
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

    private ImmutableIRedmineRemoteConfig createTestConfig() {
        return ImmutableIRedmineRemoteConfig.builder()
                .url                        ( "")
                .apiAccessKey               ( "")
                .putAllIssuesQueryParameters( new HashMap<>())
                .statusAcceptedId           ( 1) // new
                .statusProcessingId         ( 2) // in progress
                .statusDoneId               ( 2) // resolved
                .statusFailedId             ( 3) // rejected
                .connectTimeoutSeconds      ( 120) // 2 minutes
                .readTimeoutSeconds         ( 120) // 2 minutes
                .redmineCallbackUrl         ( "")
                .issueValidationScript      ( "")
                .build();
    }
}