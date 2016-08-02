package io.pne.deploy.server;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.server.bus.handlers.websocket_frame.WebSocketFrameAction;
import io.pne.deploy.server.bus.impl.BusImpl;
import io.pne.deploy.server.httphandler.ClientConnections;
import io.pne.deploy.server.websocket.AgentConnections;
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
    private final BusImpl           bus;

    public ServerApplication(IServerListener aServerListener) {
        this(new ServerParameters(), aServerListener);
    }

    public ServerApplication(ServerParameters aParameters, IServerListener aServerListener) {
        ClientConnections clients = new ClientConnections();
        AgentConnections connections = new AgentConnections();
        parameters = aParameters;
        listener   = aServerListener;
        vertx      = Vertx.vertx();
        bus = new BusImpl(clients, connections);
        verticle   = new WebSocketVerticle(parameters.port, aServerListener, bus, connections);
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
        bus.send(new WebSocketFrameAction(aHostname, aMessage));
//        verticle.sendMessage(aHostname, aMessage);
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
