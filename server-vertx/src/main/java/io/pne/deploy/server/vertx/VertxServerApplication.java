package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.process.impl.RedmineIssuesProcessServiceImpl;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.impl.RemoteRedmine4_2_10ServiceImpl;
import io.pne.deploy.client.redmine.remote.impl.RemoteTelegramServiceImpl;
import io.pne.deploy.client.redmine.remote.impl.TelegramClient;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
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
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.pne.deploy.server.vertx.dashboard.DashboardHttpHandler;
import io.pne.deploy.server.vertx.dashboard.IDashboardConfig;
import io.pne.deploy.server.vertx.metrics.MetricsHttpHandler;
import io.pne.deploy.server.vertx.metrics.QueueMetrics;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

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

        StatusHttpHandler    statusHttpHandler    = new StatusHttpHandler(agentConnections, pendingIssues);
        IDashboardConfig     dashboardConfig      = StartupParametersFactory.getStartupParameters(IDashboardConfig.class);
        DashboardHttpHandler dashboardHttpHandler = new DashboardHttpHandler(
                this.vertx, agentConnections, pendingIssues, new LinkedHashMap<>(), statusHttpHandler::getLatestTaskStatus,
                null, dashboardConfig.path(), dashboardConfig.refreshMs());

        this.verticle       = new WebSocketVerticle(aConfig.getPort(), serverListener, agentConnections, gson, response, deployService, Executors.newSingleThreadExecutor(), redmineConfig, pendingIssues, taskListener, statusHttpHandler, event -> {}, dashboardHttpHandler);
        this.serverListener = serverListener;
    }

    public VertxServerApplication() {
        agentConnections    = new AgentConnections();

        Gson                      gson              = new GsonBuilder().setPrettyPrinting().create();
        CommandResponses          response          = new CommandResponses();
        ArrayBlockingQueue<Long>  pendingIssues     = new ArrayBlockingQueue<>(1000);
        IRedmineRemoteConfig      redmineConfig     = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);

        // Prometheus metrics for both durable queues (scraped at /metrics); recorders must exist before the queues.
        PrometheusMeterRegistry   metrics           = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        LongConsumer              telegramLatency   = QueueMetrics.sendLatencyRecorder(metrics, "telegram");
        LongConsumer              redmineLatency    = QueueMetrics.sendLatencyRecorder(metrics, "redmine");

        RemoteRedmine4_2_10ServiceImpl redmine      = new RemoteRedmine4_2_10ServiceImpl(redmineConfig, redmineLatency);
        IVertxServerConfiguration config            = StartupParametersFactory.getStartupParameters(IVertxServerConfiguration.class);
        StatusHttpHandler         statusHttpHandler = new StatusHttpHandler(agentConnections, pendingIssues);
        // Single Telegram client shared by both the live-status listener and the diff notifications,
        // so all traffic goes through one rate-limited queue (one limit per chat).
        TelegramClient            telegramClient    = new TelegramClient(redmineConfig.getTelegramToken(), new File(redmineConfig.queueDir(), "telegram"), telegramLatency);
        IRemoteTelegramService    diffTelegram      = new RemoteTelegramServiceImpl(telegramClient, redmineConfig);
        ITaskExecutionListener    taskListener      = createTaskListener(statusHttpHandler, redmineConfig, telegramClient);

        QueueMetrics.register(metrics, "telegram", telegramClient.getSpool());
        QueueMetrics.register(metrics, "redmine", redmine.getSpool());
        new JvmMemoryMetrics().bindTo(metrics);
        new JvmGcMetrics().bindTo(metrics);
        new JvmThreadMetrics().bindTo(metrics);
        new ProcessorMetrics().bindTo(metrics);
        MetricsHttpHandler        metricsHttpHandler = new MetricsHttpHandler(metrics);

        deployService       = new DeployServiceImpl(new VertxAgentFinderServiceImpl(agentConnections, gson, response, taskListener), config.getAliasesDir(), taskListener);

        RedmineIssuesProcessServiceImpl redmineIssuesProcessService = new RedmineIssuesProcessServiceImpl(redmine, deployService, redmineConfig, diffTelegram);
        VertxServerApplicationListener serverListener = new VertxServerApplicationListener(redmineIssuesProcessService, pendingIssues);

        this.vertx          = Vertx.vertx();

        // Live dashboard: reads the same live state and streams it over SSE (path/interval configurable).
        Map<String, PersistentSpool> dashboardQueues = new LinkedHashMap<>();
        dashboardQueues.put("telegram", telegramClient.getSpool());
        dashboardQueues.put("redmine",  redmine.getSpool());
        IDashboardConfig dashboardConfig = StartupParametersFactory.getStartupParameters(IDashboardConfig.class);
        DashboardHttpHandler dashboardHttpHandler = new DashboardHttpHandler(
                this.vertx, agentConnections, pendingIssues, dashboardQueues, statusHttpHandler::getLatestTaskStatus,
                metrics, dashboardConfig.path(), dashboardConfig.refreshMs());

        this.verticle       = new WebSocketVerticle(config.getPort(), serverListener, agentConnections, gson, response, deployService, Executors.newSingleThreadExecutor(), redmineConfig, pendingIssues, taskListener, statusHttpHandler, metricsHttpHandler, dashboardHttpHandler);
        this.serverListener = serverListener;
    }

    private ITaskExecutionListener createTaskListener(Consumer<TaskStatus> aConsumer, IRedmineRemoteConfig aConfig, TelegramClient aTelegramClient) {

        return new TaskExecutionListenerQueue(
                new TaskExecutionListenerCompound(
                        new TaskExecutionListenerLogger(),
                        new TaskExecutionListenerStatus(aConsumer),
                        aConfig.isTelegramEnabled() ? new TaskExecutionListenerTelegram(aConfig.getTelegramChatId(), aTelegramClient) : null
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
