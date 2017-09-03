package io.pne.deploy.agent.websocket;

import io.pne.deploy.agent.api.IAgentChannelService;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;

public class WebSocketAgentChannelService implements IAgentChannelService {

    private final WebSocketOutputQueue queue;

    public WebSocketAgentChannelService(WebSocketOutputQueue queue) {
        this.queue = queue;
    }

    @Override
    public void sendLog(RunAgentCommandLog aLogMessage) {
        queue.enqueue(aLogMessage);
    }
}
