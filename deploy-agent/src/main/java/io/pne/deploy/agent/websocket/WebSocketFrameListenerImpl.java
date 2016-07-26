package io.pne.deploy.agent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Charsets;
import com.payneteasy.websocket.HexUtil;
import com.payneteasy.websocket.IWebSocketListener;
import com.payneteasy.websocket.MutableWebSocketFrame;
import com.payneteasy.websocket.WebSocketContext;
import io.pne.deploy.agent.tasks.ITaskService;
import io.pne.deploy.agent.websocket.handlers.HeartbeatClientHandler;
import io.pne.deploy.agent.websocket.handlers.ShellScriptParametersClientHandler;
import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.pne.deploy.api.MessageTypes.findType;

public class WebSocketFrameListenerImpl implements IWebSocketListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketFrameListenerImpl.class);

    private final ObjectMapper mapper;

    private final Map<Class, IClientHandler> handlers;

    public WebSocketFrameListenerImpl(ITaskService aTaskService, IMessageSender aSender) {
        mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());

        handlers = new HashMap<>();
        handlers.put(ShellScriptParameters.class, new ShellScriptParametersClientHandler(aTaskService, aSender));
        handlers.put(Heartbeat.class, new HeartbeatClientHandler(aSender));
    }

    @Override
    public void onMessage(MutableWebSocketFrame aFrame, WebSocketContext aContext) {
        byte[] buf = aFrame.getBinaryData();
        if(buf.length < 2) {
            LOG.warn("Frame is less then 2 [{}]", buf.length);
            return;
        }

        int version = buf[0];
        int typeId  = buf[1];

        Object         message = createMessage(typeId, buf);
        IClientHandler handler = findHandler(typeId);

        //noinspection unchecked
        handler.handle(message, new ClientHandlerContextImpl());
    }

    private IClientHandler findHandler(int aTypeId) {
        Class type = findType(aTypeId);
        IClientHandler handler = handlers.get(type);
        if(handler == null) {
            throw new IllegalStateException("Could not find handler for type " + type.getSimpleName());
        }
        return handler;
    }

    private Object createMessage(int aTypeId, byte[] aBuf) {
        Class type = findType(aTypeId);
        try {
            return mapper.readValue(aBuf, 2, aBuf.length - 2, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read from buffer "
                    + HexUtil.toFormattedHexString(aBuf, 2, aBuf.length-2)
                    + " "
                    + new String(aBuf, 2, aBuf.length -2, Charsets.UTF_8)
                    , e);
        }
    }

    @Override
    public void onFailure(Throwable aError) {
         // is not invoked
    }
}
