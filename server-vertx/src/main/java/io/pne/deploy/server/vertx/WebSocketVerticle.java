package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;

public class WebSocketVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WebSocketVerticle.class);

    private final ServerWebSocketFrameHandler serverWebSocketFrameHandler;
    private final AgentConnections            connections;
    private final int                         port;
    private       HttpServer                  httpServer;

    public WebSocketVerticle(int aPort, IServerListener aServerListener, AgentConnections aConnections, Gson aGson) {
        this.serverWebSocketFrameHandler = new ServerWebSocketFrameHandler(aServerListener, aGson);
        port = aPort;
        connections = aConnections;
    }

    @Override
    public void start(Future<Void> aStartFuture) throws Exception {
        LOG.info("Starting http server on port {}...", port);

        httpServer = vertx.createHttpServer()
                .websocketHandler(aSocket -> {
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
                .requestHandler(new HttpHandler())
                .listen(port, event -> {
                    if(event.failed()) {
                        aStartFuture.fail(event.cause());
                    } else {
                        aStartFuture.complete();
                    }
                })
        ;
    }

    @Override
    public void stop(Future<Void> aStopFuture) throws Exception {
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
