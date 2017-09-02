package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.api.messages.RunAgentCommandMessage;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class VertxAgentServiceImpl implements IAgentService {

    private static final Logger LOG = LoggerFactory.getLogger(VertxAgentServiceImpl.class);

    private final AgentConnections connections;
    private final Gson             gson;

    public VertxAgentServiceImpl(AgentConnections aConnections, Gson aGson) {
        connections = aConnections;
        gson = aGson;
    }

    @Override
    public void runCommand(RunAgentCommandMessage aCommand) throws AgentCommandException {
        LOG.debug("Sending command {} ", aCommand);
        sendMessage(aCommand.agentId, aCommand);
    }


    private Buffer createBinaryFrame(IAgentServerMessage aServerMessage) {
        Buffer buffer = Buffer.buffer();
        buffer.appendByte((byte) 0x01);
        buffer.appendByte((byte) AgentMessageType.findByClass(aServerMessage.getClass()).id);

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

    private void sendMessage(String aHostname, IAgentServerMessage aMessage) {
        ServerWebSocket socket = connections.getSocket(aHostname);
        Buffer buffer = createBinaryFrame(aMessage);
        socket.writeBinaryMessage(buffer);
    }

}
