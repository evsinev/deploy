package io.pne.deploy.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.api.messages.ImmutableHeartbeat;
import io.pne.deploy.server.bus.IBus;
import io.pne.deploy.server.httphandler.HttpHandler;
import io.pne.deploy.server.websocket.AgentConnections;
import io.pne.deploy.server.websocket.ServerWebSocketFrameHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;

import static io.pne.deploy.api.MessageTypes.findTypeId;

public class WebSocketVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WebSocketVerticle.class);

    private final ServerWebSocketFrameHandler serverWebSocketFrameHandler;
    private final AgentConnections connections;
    private final int                         port;
    private       HttpServer                  httpServer;
    private final IBus                        bus;

    public WebSocketVerticle(int aPort, IServerListener aServerListener, IBus aBus, AgentConnections aConnections) {
        this.serverWebSocketFrameHandler = new ServerWebSocketFrameHandler(aServerListener, aBus);
        port = aPort;
        bus = aBus;
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

                    connections.add(aSocket);

                    aSocket.frameHandler(serverWebSocketFrameHandler);

                    aSocket.closeHandler(aVoid -> {
                        connections.remove(aSocket);
                        LOG.debug("CLOSED");
                    });

                    aSocket.endHandler(aVoid -> {
                        LOG.debug("END");
                    });

                    aSocket.handler( buf -> {
                        LOG.debug("BUFFER - > {}", buf);
                    });

                    LOG.debug("Socket {}", aSocket);

                    // Sends heartbeat
                    ImmutableHeartbeat hb = ImmutableHeartbeat.builder()
                            .requestId("123")
                            .build();
                    Buffer buffer = createBinaryFrame(hb);
                    aSocket.writeBinaryMessage(buffer);

                })
                .requestHandler(new HttpHandler(bus))
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

    private Buffer createBinaryFrame(IServerMessage aServerMessage) {
        Buffer buffer = Buffer.buffer();
        buffer.appendByte((byte) 0x01);
        buffer.appendByte((byte) findTypeId(aServerMessage.getClass()));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());

        buffer.appendBytes(createBytes(mapper, aServerMessage));
        return buffer;
    }

    private byte[] createBytes(ObjectMapper mapper, IServerMessage hb) {
        try {
            return mapper.writeValueAsBytes(hb);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create json", e);
        }
    }

    public void sendMessage(String aHostname, IServerMessage aMessage) {
        ServerWebSocket socket = connections.getSocket(aHostname);
        Buffer buffer = createBinaryFrame(aMessage);
        socket.writeBinaryMessage(buffer);
    }
}
