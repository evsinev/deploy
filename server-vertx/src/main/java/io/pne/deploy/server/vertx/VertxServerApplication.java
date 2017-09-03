package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.server.IServerApplicationListener;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.service.impl.DeployServiceImpl;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

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
        ServerApplicationListenerNoOp serverListenerNoOp   = new ServerApplicationListenerNoOp();

        VertxServerApplication application = new VertxServerApplication(
                 serverListenerNoOp
                , new VertxServerConfigurationImpl()
        );

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

    public VertxServerApplication(IServerApplicationListener serverListener, IVertxServerConfiguration aConfig) {
        Gson gson           = new GsonBuilder().setPrettyPrinting().create();
        CommandResponses response = new CommandResponses();
        this.vertx          = Vertx.vertx();

        agentConnections    = new AgentConnections();
        this.verticle       = new WebSocketVerticle(aConfig.getPort(), serverListener, agentConnections, gson, response);
        this.serverListener = serverListener;
        deployService       = new DeployServiceImpl(new VertxAgentFinderServiceImpl(agentConnections, gson, response), aConfig.getAliasesDir());
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
