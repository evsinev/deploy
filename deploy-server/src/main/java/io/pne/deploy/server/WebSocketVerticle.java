package io.pne.deploy.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.payneteasy.websocket.HexUtil;
import io.pne.deploy.api.MessageTypes;
import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.messages.HeartbeatAck;
import io.pne.deploy.api.messages.ImmutableHeartbeat;
import io.pne.deploy.server.websocket.ServerWebSocketFrameHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;

import java.io.IOException;

import static io.pne.deploy.api.MessageTypes.findTypeId;

public class WebSocketVerticle extends AbstractVerticle {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(WebSocketVerticle.class);

    ServerWebSocketFrameHandler serverWebSocketFrameHandler = new ServerWebSocketFrameHandler();

    @Override
    public void start() throws Exception {
        LOG.debug("Starting ...");

        vertx.createHttpServer()
                .websocketHandler(aSocket -> {
                    LOG.debug("URI              : {}", aSocket.uri());
                    LOG.debug("query            : {}", aSocket.query());
                    LOG.debug("path             : {}", aSocket.path());
                    LOG.debug("textHandlerID    : {}", aSocket.textHandlerID());
                    LOG.debug("binaryHandlerID  : {}", aSocket.binaryHandlerID());


                    aSocket.handler(buffer -> {

                    });

                    Buffer buffer = Buffer.buffer();
                    buffer.appendByte((byte) 0x01);
                    buffer.appendByte((byte) findTypeId(Heartbeat.class));

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new Jdk8Module());

                    ImmutableHeartbeat hb = ImmutableHeartbeat.builder()
                            .requestId("123")
                            .build();
                    buffer.appendBytes(createBytes(mapper, hb));

                    aSocket.writeBinaryMessage(buffer);

//                    aSocket.frameHandler(aFrame -> {
//                        if(aFrame.isBinary()) {
//                            Buffer buf = aFrame.binaryData();
//
//                            LOG.debug("    binary data: {}", HexUtil.toFormattedHexString(buf.getBytes()));
//                            try {
//                                String text = new String(buf.getBytes(), 2, buf.length() - 2);
//                                System.out.println("text = " + text);
//                                HeartbeatAck ack = mapper.readValue(buf.getBytes(), 2, buf.length() - 2, HeartbeatAck.class);
//                                System.out.println("ack = " + ack);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
//
//                        } else {
//                            LOG.debug("    text data: {}", aFrame.textData());
//                        }
////                        aSocket.write(Buffer.buffer("Hello Final Message"))
////                                .end();
//                    });

                    aSocket.frameHandler(serverWebSocketFrameHandler);

                    aSocket.closeHandler(aVoid -> {
                        LOG.debug("CLOSED");
                    });

                    aSocket.endHandler(aVoid -> {
                        LOG.debug("END");
                    });

                    aSocket.handler( buf -> {
                        LOG.debug("BUFFER - > {}", buf);
                    });

                    LOG.debug("Socket {}", aSocket);

                })
                .requestHandler(aRequest -> {
                    LOG.debug("request {}", aRequest);
                    aRequest
                            .response()
                            .end("hello\n");
                })
                .listen(9090)
        ;
    }

    private byte[] createBytes(ObjectMapper mapper, ImmutableHeartbeat hb) {
        try {
            return mapper.writeValueAsBytes(hb);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create json", e);
        }
    }
}
