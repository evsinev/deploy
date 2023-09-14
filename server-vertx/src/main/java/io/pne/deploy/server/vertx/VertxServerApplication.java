package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.process.impl.RedmineIssuesProcessServiceImpl;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.impl.RemoteRedmine4_2_10ServiceImpl;
import io.pne.deploy.server.IServerApplicationListener;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.api.impl.TaskExecutionListenerLogger;
import io.pne.deploy.server.service.impl.DeployServiceImpl;
import io.pne.deploy.server.vertx.status.StatusHttpHandler;
import io.pne.deploy.server.vertx.status.TaskExecutionListenerCompound;
import io.pne.deploy.server.vertx.status.TaskExecutionListenerQueue;
import io.pne.deploy.server.vertx.status.TaskExecutionListenerStatus;
import io.pne.deploy.server.vertx.status.TaskExecutionListenerTelegram;
import io.pne.deploy.server.vertx.status.model.TaskStatus;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class VertxServerApplication {

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
    
    private static final Logger LOG = LoggerFactory.getLogger(VertxServerApplication.class);

    private final Vertx                      vertx;
    private final WebSocketVerticle          verticle;
    private final IServerApplicationListener serverListener;
    private final AgentConnections           agentConnections;
    private final DeployServiceImpl          deployService;

    public static void main(String[] args) {

        VertxServerApplication application = new VertxServerApplication();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            {
                setName("server-shutdown-hook");
            }
            @Override
            public void run() {
                LOG.info("Executing a shutdown hook ...");
                application.stop();
            }
        });

        application.start();

    }

    // for test only
    public VertxServerApplication(IServerApplicationListener serverListener, IVertxServerConfiguration aConfig, IRedmineRemoteConfig redmineConfig) {
        Gson                   gson         = new GsonBuilder().setPrettyPrinting().create();
        CommandResponses       response     = new CommandResponses();
        ITaskExecutionListener taskListener = new TaskExecutionListenerLogger();

        this.vertx = Vertx.vertx();

        agentConnections    = new AgentConnections();
        deployService       = new DeployServiceImpl(new VertxAgentFinderServiceImpl(agentConnections, gson, response, taskListener), aConfig.getAliasesDir(), taskListener);

        ArrayBlockingQueue<Long>     pendingIssues = new ArrayBlockingQueue<>(1000);

        this.verticle       = new WebSocketVerticle(aConfig.getPort(), serverListener, agentConnections, gson, response, deployService, Executors.newSingleThreadExecutor(), redmineConfig, pendingIssues, taskListener, event -> {});
        this.serverListener = serverListener;
    }

    public VertxServerApplication() {
        agentConnections    = new AgentConnections();

        Gson                      gson              = new GsonBuilder().setPrettyPrinting().create();
        CommandResponses          response          = new CommandResponses();
        ArrayBlockingQueue<Long>  pendingIssues     = new ArrayBlockingQueue<>(1000);
        IRedmineRemoteConfig      redmineConfig     = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        RemoteRedmine4_2_10ServiceImpl  redmine     = new RemoteRedmine4_2_10ServiceImpl(redmineConfig);
        IVertxServerConfiguration config            = StartupParametersFactory.getStartupParameters(IVertxServerConfiguration.class);
        StatusHttpHandler         statusHttpHandler = new StatusHttpHandler(agentConnections, pendingIssues);
        ITaskExecutionListener    taskListener      = createTaskListener(statusHttpHandler, redmineConfig);

        deployService       = new DeployServiceImpl(new VertxAgentFinderServiceImpl(agentConnections, gson, response, taskListener), config.getAliasesDir(), taskListener);

        RedmineIssuesProcessServiceImpl redmineIssuesProcessService = new RedmineIssuesProcessServiceImpl(redmine, deployService, redmineConfig);
        VertxServerApplicationListener serverListener = new VertxServerApplicationListener(redmineIssuesProcessService, pendingIssues);

        this.vertx          = Vertx.vertx();

        this.verticle       = new WebSocketVerticle(config.getPort(), serverListener, agentConnections, gson, response, deployService, Executors.newSingleThreadExecutor(), redmineConfig, pendingIssues, taskListener, statusHttpHandler);
        this.serverListener = serverListener;
    }

    private ITaskExecutionListener createTaskListener(Consumer<TaskStatus> aConsumer, IRedmineRemoteConfig aConfig) {

        return new TaskExecutionListenerQueue(
                new TaskExecutionListenerCompound(
                        new TaskExecutionListenerLogger(),
                        new TaskExecutionListenerStatus(aConsumer),
                        aConfig.isTelegramEnabled() ? new TaskExecutionListenerTelegram(aConfig.getTelegramChatId(), aConfig.getTelegramToken()) : null
                )
        );
    }


    public void start() {
        vertx.deployVerticle(verticle, event -> {
            if(event.succeeded()) {
                serverListener.didStarted();
            } else {
                serverListener.didStartFailed(event.cause());
            }
        });
    }

    public void stop() {
        LOG.info("Exiting ...");
        vertx.close();
        serverListener.serverStopped();
    }

    public IDeployService getDeployService() {
        return deployService;
    }
}
