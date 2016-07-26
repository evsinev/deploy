package io.pne.deploy.agent.websocket.handlers;

import io.pne.deploy.agent.websocket.IClientHandler;
import io.pne.deploy.agent.websocket.IClientHandlerContext;
import io.pne.deploy.agent.websocket.IMessageSender;
import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.messages.ImmutableHeartbeatAck;

public class HeartbeatClientHandler implements IClientHandler<Heartbeat> {

    private final IMessageSender sender;

    public HeartbeatClientHandler(IMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void handle(Heartbeat aMessage, IClientHandlerContext aContext) {

        sender.enqueue(
                ImmutableHeartbeatAck.builder()
                .responseId(aMessage.requestId())
                .build()
        );
    }
}
