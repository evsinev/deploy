package io.pne.deploy.server;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.pne.deploy.api.IServerMessage;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ServerApplication.class);

    private final IServerListener   listener;
    private final Vertx             vertx;
    private final WebSocketVerticle verticle;
    private final ServerParameters  parameters;

    public ServerApplication(IServerListener aServerListener) {
        this(new ServerParameters(), aServerListener);
    }

    public ServerApplication(ServerParameters aParameters, IServerListener aServerListener) {
        parameters = aParameters;
        listener   = aServerListener;
        vertx      = Vertx.vertx();
        verticle   = new WebSocketVerticle(parameters.port, aServerListener);
    }

    public void start() {
        vertx.deployVerticle(verticle, event -> {
            if(event.succeeded()) {
                listener.didStarted();
            } else {
                listener.didStartFailed(event.cause());
            }
        });
    }


    public void stop() {
        LOG.info("Exiting ...");
        vertx.close(event -> {
            listener.serverStopped();
        });
    }

    public void sendMessage(String aHostname, IServerMessage aMessage) {
        verticle.sendMessage(aHostname, aMessage);
    }

    public static void main(String[] args) throws IOException {

        ServerParameters parameters = new ServerParameters();
        try {
            new JCommander(parameters, args);
            LOG.info("Parameters: {}", parameters);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println();
            StringBuilder sb = new StringBuilder();
            new JCommander(parameters).usage(sb);
            System.err.println(sb);
            return;
        }

        ServerApplication server = new ServerApplication(parameters, new ServerListenerNoOp());

        Runtime.getRuntime().addShutdownHook(new Thread() {
            {
                setName("server-shutdown-hook");
            }
            @Override
            public void run() {
                LOG.info("Executing a shutdown hook ...");
                server.stop();
            }
        });

        server.start();

    }

}
