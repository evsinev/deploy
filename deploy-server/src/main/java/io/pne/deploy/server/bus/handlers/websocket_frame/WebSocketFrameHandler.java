package io.pne.deploy.server.bus.handlers.websocket_frame;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.server.bus.IHandler;
import io.pne.deploy.server.bus.IHandlerContext;
import io.pne.deploy.server.websocket.AgentConnections;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;

import static io.pne.deploy.api.MessageTypes.findTypeId;

public class WebSocketFrameHandler implements IHandler<WebSocketFrameAction> {

    private final AgentConnections connections;

    public WebSocketFrameHandler(AgentConnections connections) {
        this.connections = connections;
    }

    @Override
    public void handle(WebSocketFrameAction aAction, IHandlerContext aContext) {
        sendMessage(aAction.hostname, aAction.message);
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
