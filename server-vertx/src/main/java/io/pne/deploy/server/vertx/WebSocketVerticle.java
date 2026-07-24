package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.IServerApplicationListener;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.ITaskExecutionListener;
import io.pne.deploy.server.vertx.dashboard.DashboardHttpHandler;
import io.pne.deploy.server.vertx.http.HttpHandler;
import io.pne.deploy.server.vertx.status.StatusHttpHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;

import java.util.Collection;
import java.util.concurrent.Executor;

public class WebSocketVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WebSocketVerticle.class);

    private final ServerWebSocketFrameHandler serverWebSocketFrameHandler;
    private final AgentConnections            connections;
    private final int                         port;
    private       HttpServer                  httpServer;
    private final IDeployService              deployService;
    private final Executor                    commandExecutor;
    private final IRedmineRemoteConfig        redmineConfig;
    private final Collection<Long>            issues;
    private final Handler<HttpServerRequest>           statusHttpHandler;
    private final Handler<HttpServerRequest>           metricsHttpHandler;
    private final DashboardHttpHandler                 dashboardHttpHandler;

    public WebSocketVerticle(int aPort
            , IServerApplicationListener aServerListener
            , AgentConnections aConnections
            , Gson aGson
            , CommandResponses aCommandResponses
            , IDeployService   aDeploService
            , Executor         aCommandExecutor
            , IRedmineRemoteConfig aRedmineConfig
            , Collection<Long>  aIssues
            , ITaskExecutionListener aListener
            , Handler<HttpServerRequest> aStatusHttpHandler
            , Handler<HttpServerRequest> aMetricsHttpHandler
            , DashboardHttpHandler aDashboardHttpHandler
    ) {
        this.serverWebSocketFrameHandler = new ServerWebSocketFrameHandler(aServerListener, aGson, aCommandResponses, aListener);
        port = aPort;
        connections = aConnections;
        deployService = aDeploService;
        commandExecutor = aCommandExecutor;
        redmineConfig = aRedmineConfig;
        issues = aIssues;
        statusHttpHandler = aStatusHttpHandler;
        metricsHttpHandler = aMetricsHttpHandler;
        dashboardHttpHandler = aDashboardHttpHandler;
    }

    @Override
    public void start(Promise<Void> aStartFuture) throws Exception {
        LOG.info("Starting http server on port {}...", port);

        httpServer = vertx.createHttpServer()
                .webSocketHandler(aSocket -> {
                    LOG.debug("URI              : {}", aSocket.uri());
                    LOG.debug("query            : {}", aSocket.query());
                    LOG.debug("path             : {}", aSocket.path());
                    LOG.debug("textHandlerID    : {}", aSocket.textHandlerID());
                    LOG.debug("binaryHandlerID  : {}", aSocket.binaryHandlerID());

                    connections.addAgent(aSocket);

                    aSocket.frameHandler(serverWebSocketFrameHandler);

                    aSocket.closeHandler(aVoid -> {
                        connections.removeAgent(aSocket);
                        LOG.debug("CLOSED");
                    });

                    aSocket.endHandler(aVoid -> {
                        LOG.debug("END");
                    });

                    aSocket.handler( buf -> {
                        LOG.debug("BUFFER - > {}", buf);
                    });

                    LOG.debug("Socket {}", aSocket);

//                    // Sends heartbeat
//                    ImmutableHeartbeat hb = ImmutableHeartbeat.builder()
//                            .requestId("123")
//                            .build();
//                    Buffer buffer = createBinaryFrame(hb);
//                    aSocket.writeBinaryMessage(buffer);

                })
                .requestHandler(new HttpHandler(connections, deployService, commandExecutor, redmineConfig, issues, statusHttpHandler, metricsHttpHandler, dashboardHttpHandler))
                .listen(port, "127.0.0.1", event -> {
                    if(event.failed()) {
                        aStartFuture.fail(event.cause());
                    } else {
                        aStartFuture.complete();
                    }
                });
    }

    @Override
    public void stop(Promise<Void> aStopFuture) throws Exception {
        httpServer.close(event -> {
            if(event.failed()) {
                aStopFuture.fail(event.cause());
            } else {
                aStopFuture.complete();
            }
        });
    }

//    private Buffer createBinaryFrame(IServerMessage aServerMessage) {
//        Buffer buffer = Buffer.buffer();
//        buffer.appendByte((byte) 0x01);
//        buffer.appendByte((byte) findTypeId(aServerMessage.getClass()));
//
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new Jdk8Module());
//
//        buffer.appendBytes(createBytes(mapper, aServerMessage));
//        return buffer;
//    }
//
//    private byte[] createBytes(ObjectMapper mapper, IServerMessage hb) {
//        try {
//            return mapper.writeValueAsBytes(hb);
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException("Could not create json", e);
//        }
//    }
//
//    public void sendMessage(String aHostname, IServerMessage aMessage) {
//        ServerWebSocket socket = connections.getSocket(aHostname);
//        Buffer buffer = createBinaryFrame(aMessage);
//        socket.writeBinaryMessage(buffer);
//    }
}
