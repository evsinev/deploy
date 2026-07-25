package io.pne.deploy.agent.websocket;

import com.google.gson.Gson;
import com.payneteasy.websocket.IWebSocketListener;
import com.payneteasy.websocket.MutableWebSocketFrame;
import com.payneteasy.websocket.WebSocketContext;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.api.messages.GetVersionRequest;
import io.pne.deploy.agent.api.messages.GetVersionResponse;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.agent.commands.VersionFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketListenerImpl implements IWebSocketListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketListenerImpl.class);

    private final IAgentService agentService;
    private final Gson          gson;
    private final WebSocketOutputQueue queue;

    public WebSocketListenerImpl(IAgentService agentService, Gson aGson, WebSocketOutputQueue aQueue) {
        this.agentService = agentService;
        gson = aGson;
        queue = aQueue;
    }

    @Override
    public void onMessage(MutableWebSocketFrame aFrame, WebSocketContext aContext) {
        byte[] buf = aFrame.getBinaryData();
        if(buf.length < 2) {
            LOG.warn("Frame is less then 2 [{}]", buf.length);
            return;
        }

        int  version = buf[0];
        byte typeId  = buf[1];

        AgentMessageType    type    = AgentMessageType.findType(typeId);
        IAgentServerMessage message = (IAgentServerMessage) gson.fromJson(new String(buf, 2, buf.length - 2), type.clazz);

        switch (type) {
            case RUN_COMMAND_REQUEST:
                RunAgentCommandRequest runCommandRequest = (RunAgentCommandRequest) message;
                try {
                    agentService.runCommand(runCommandRequest);
                    queue.enqueue(new RunAgentCommandResponse(runCommandRequest.commandId));
                } catch (AgentCommandException e) {
                    LOG.error("Couldn't execute", e);
                    queue.enqueue(new RunAgentCommandResponse(runCommandRequest.commandId, e));
                }
                break;

            case GET_VERSION_REQUEST:
                GetVersionRequest versionRequest = (GetVersionRequest) message;
                try {
                    String fetchedVersion = VersionFetcher.fetch(versionRequest.versionUrl);
                    LOG.info("GetVersion {} -> {}", versionRequest.versionUrl, fetchedVersion);
                    queue.enqueue(new GetVersionResponse(versionRequest.requestId, fetchedVersion, null));
                } catch (Exception e) {
                    LOG.warn("Can't fetch version from {}", versionRequest.versionUrl, e);
                    queue.enqueue(new GetVersionResponse(versionRequest.requestId, null, e.toString()));
                }
                break;

            default:
                LOG.warn("Unsupported message type {} — ignoring", type);

        }
    }

    @Override
    public void onFailure(Throwable aError) {

    }
}
