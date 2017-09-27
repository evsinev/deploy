package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class VertxAgentServiceImpl implements IAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(VertxAgentServiceImpl.class);

    private final AgentConnections connections;
    private final Gson             gson;
    private final CommandResponses commandResponses;

    public VertxAgentServiceImpl(AgentConnections aConnections, Gson aGson, CommandResponses aCommandResponses) {
        connections = aConnections;
        gson = aGson;
        commandResponses = aCommandResponses;
    }

    @Override
    public void runCommand(RunAgentCommandRequest aCommand) throws AgentCommandException {
        LOG.debug("Sending command {} ", aCommand);
        try {
            sendMessage(aCommand.agentId, aCommand);
        } catch (InterruptedException e) {
            throw new AgentCommandException("Interrupted", e);
        }
        // waiting for response
        try {
            RunAgentCommandResponse response = commandResponses.awaitForCommandResponse(aCommand.commandId);
            LOG.info("Response for command {} is {}", aCommand, response);
            if(response.error != null) {
                throw new AgentCommandException("Agent " + aCommand.agentId + "return error: " + response.error, response.error);
            }
        } catch (InterruptedException e) {
            throw new AgentCommandException("Interrupted");
        }
    }


    private Buffer createBinaryFrame(IAgentServerMessage aServerMessage) {
        Buffer buffer = Buffer.buffer();
        buffer.appendByte((byte) 0x01);
        buffer.appendByte( AgentMessageType.findByClass(aServerMessage.getClass()).id);

        buffer.appendBytes(createBytes(aServerMessage));


//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new Jdk8Module());

        return buffer;
    }

    private byte[] createBytes(IAgentServerMessage aMessage) {
        String json = gson.toJson(aMessage);
        LOG.debug("Json = {}", json);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private void sendMessage(String aHostname, IAgentServerMessage aMessage) throws InterruptedException {
        ServerWebSocket socket = connections.getSocket(aHostname);
        Buffer buffer = createBinaryFrame(aMessage);
        socket.writeBinaryMessage(buffer);
    }

}
