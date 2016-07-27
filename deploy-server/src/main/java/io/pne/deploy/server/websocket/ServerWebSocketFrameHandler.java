package io.pne.deploy.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.pne.deploy.api.IClientMessage;
import io.pne.deploy.api.MessageTypes;
import io.pne.deploy.server.IServerListener;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ServerWebSocketFrameHandler implements Handler<WebSocketFrame> {


    private static final Logger LOG = LoggerFactory.getLogger(ServerWebSocketFrameHandler.class);

    private final ObjectMapper    mapper;
    private final IServerListener serverListener;

    public ServerWebSocketFrameHandler(IServerListener aServerListener) {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        serverListener = aServerListener;
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

        IClientMessage message = createMessage(typeId, buffer);
        serverListener.didReceiveMessage(message);

        LOG.info("Got {}", message);
    }

    private IClientMessage createMessage(byte aTypeId, Buffer aBuffer) {
        Class type = MessageTypes.findType(aTypeId);
        try {
            return (IClientMessage) mapper.readValue(aBuffer.getBytes(2, aBuffer.length()), type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse", e);
        }
    }


}
