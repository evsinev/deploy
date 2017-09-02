package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.service.impl.DeployServiceImpl;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerVertxApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ServerVertxApplication.class);

    private final Vertx             vertx;
    private final WebSocketVerticle verticle;
    private final IServerListener   serverListener;
    private final AgentConnections  agentConnections;
    private DeployServiceImpl       deployService;

    public static void main(String[] args) {
        ServerListenerNoOp serverListenerNoOp   = new ServerListenerNoOp();

        ServerVertxApplication application = new ServerVertxApplication(
                 serverListenerNoOp
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

    public ServerVertxApplication(IServerListener serverListener) {
        Gson gson           = new GsonBuilder().setPrettyPrinting().create();
        this.vertx          = Vertx.vertx();
        agentConnections    = new AgentConnections();
        this.verticle       = new WebSocketVerticle(8080, serverListener, agentConnections, gson);
        this.serverListener = serverListener;
        deployService       = new DeployServiceImpl(new VertxAgentFinderServiceImpl(agentConnections, gson));
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
