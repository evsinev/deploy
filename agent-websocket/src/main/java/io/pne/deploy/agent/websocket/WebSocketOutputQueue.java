package io.pne.deploy.agent.websocket;

import com.google.gson.Gson;
import com.payneteasy.websocket.IOutputQueue;
import com.payneteasy.websocket.WebSocketFrame;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.payneteasy.websocket.WebSocketFrameBuilder.createBinaryFrameWithMask;

public class WebSocketOutputQueue implements IOutputQueue {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketOutputQueue.class);

    private final ArrayBlockingQueue<IAgentClientMessage> queue;
    private final ArrayBlockingQueue<WebSocketFrame>      resendQueue;
    private final Gson gson;

    public WebSocketOutputQueue(Gson aGson) {
        gson = aGson;
        queue = new ArrayBlockingQueue<>(10_240);
        resendQueue = new ArrayBlockingQueue<WebSocketFrame>(10_240);
    }

    @Override
    public void addFrame(WebSocketFrame aFrame) {
        throw new IllegalStateException("Use WebSocketOutputQueue.enqueue() instead");
    }


    @Override
    public WebSocketFrame nextFrame(long aTimeout, TimeUnit aUnit) throws InterruptedException {
        WebSocketFrame resentFrame = resendQueue.poll();
        if (resentFrame != null) {
            return resentFrame;
        }

        IAgentClientMessage message = queue.poll(aTimeout, aUnit);
        if(message == null) {
            return null;
        }

        return createBinaryFrameWithMask(createPacket(message));
    }

    @Override
    public void insertFrameAgain(WebSocketFrame aFrame) {
        resendQueue.add(aFrame);
    }

    public void enqueue(IAgentClientMessage aMessage) {
        queue.add(aMessage);
    }

    public byte[] createPacket(Object message) {
        try {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                out.write(0x01);
                out.write(AgentMessageType.findByClass(message.getClass()).id);
                String json = gson.toJson(message);
                LOG.debug("Json = {}", json);
                out.write(json.getBytes(StandardCharsets.UTF_8));
                return out.toByteArray();
            }
        } catch (IOException e) {
            LOG.error("Could not write " + message, e);
            return null;
        }
    }
}
