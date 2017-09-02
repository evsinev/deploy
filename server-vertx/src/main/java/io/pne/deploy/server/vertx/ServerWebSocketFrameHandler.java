package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class ServerWebSocketFrameHandler implements Handler<WebSocketFrame> {


    private static final Logger LOG = LoggerFactory.getLogger(ServerWebSocketFrameHandler.class);

    private final Gson gson;
    private final IServerListener serverListener;

    public ServerWebSocketFrameHandler(IServerListener aServerListener, Gson aGson) {
        gson = aGson;
        serverListener = aServerListener;
    }

    @Override
    public void handle(WebSocketFrame aEvent) {
        if (!aEvent.isBinary()) {
            LOG.warn("Frame is not binary {}", aEvent);
            return;
        }

        Buffer buffer = aEvent.binaryData();
        byte version = buffer.getByte(0);
        byte typeId  = buffer.getByte(1);

        LOG.debug("Parsing version {} and type {} ...", version, typeId);
        AgentMessageType    type    = AgentMessageType.findType(typeId);
        IAgentClientMessage message = parseMessage(type, buffer);

        serverListener.didReceiveMessage(message);
        LOG.info("Got {}", message);

        switch (type) {
            case RUN_TASK:
                // todo deal with message
                break;

            default:
                LOG.error("Unsupported type : " + type);
        }

    }

    private IAgentClientMessage parseMessage(AgentMessageType aType, Buffer aBuffer) {
        //noinspection unchecked
        Class<IAgentClientMessage> type = aType.clazz;
        String json = new String(aBuffer.getBytes(2, aBuffer.length()), StandardCharsets.UTF_8);
        LOG.debug("json: {}", json);
        return gson.fromJson(json, type);
    }


}
