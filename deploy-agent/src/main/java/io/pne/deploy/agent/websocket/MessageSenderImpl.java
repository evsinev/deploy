package io.pne.deploy.agent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.payneteasy.websocket.IOutputQueue;
import com.payneteasy.websocket.WebSocketFrame;
import io.pne.deploy.api.IClientMessage;
import io.pne.deploy.api.MessageTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.payneteasy.websocket.WebSocketFrameBuilder.createBinaryFrameWithMask;

public class MessageSenderImpl implements IMessageSender, IOutputQueue {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSenderImpl.class);

    private final ArrayBlockingQueue<IClientMessage> queue;
    private final ObjectMapper objectMapper;

    public MessageSenderImpl() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        queue = new ArrayBlockingQueue<>(10_240);
    }

    @Override
    public void enqueue(IClientMessage aMessage) {
        queue.add(aMessage);
    }

    @Override
    public void addFrame(WebSocketFrame aFrame) {
        throw new IllegalStateException("Use IMessageSender.enqueue() instead");
    }

    @Override
    public WebSocketFrame nextFrame(long aTimeout, TimeUnit aUnit) throws InterruptedException {
        IClientMessage message = queue.poll(aTimeout, aUnit);
        if(message == null) {
            return null;
        }

        return createBinaryFrameWithMask(createPacket(message));
    }

    public byte[] createPacket(Object message) {
        try {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                out.write(0x01);
                out.write(MessageTypes.findTypeId(message.getClass()));
                objectMapper.writeValue(out, message);

                return out.toByteArray();
            }
        } catch (IOException e) {
            LOG.error("Could not write " + message, e);
            return null;
        }


    }
}
