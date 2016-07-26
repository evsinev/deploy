package io.pne.deploy.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.pne.deploy.api.MessageTypes;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerWebSocketFrameHandler implements Handler<WebSocketFrame> {


    private static final Logger LOG = LoggerFactory.getLogger(ServerWebSocketFrameHandler.class);
    private final ObjectMapper mapper;

    public ServerWebSocketFrameHandler() {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());

    }

    @Override
    public void handle(WebSocketFrame aEvent) {
        if(!aEvent.isBinary()) {
            LOG.warn("Frame is not binary {}", aEvent);
            return;
        }

        Buffer buffer = aEvent.binaryData();
        byte version  = buffer.getByte(0);
        byte typeId   = buffer.getByte(1);

        Object         message = createMessage(typeId, buffer);

        LOG.info("Got {}", message);
    }

    private Object createMessage(byte aTypeId, Buffer aBuffer) {
        Class type = MessageTypes.findType(aTypeId);
        try {
            return mapper.readValue(aBuffer.getBytes(2, aBuffer.length()), type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse", e);
        }
    }


}
