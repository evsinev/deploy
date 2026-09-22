package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.AgentInfo;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.api.ITaskExecutionListener;
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
    private final ITaskExecutionListener listener;
    private final AgentRegistry    registry;

    public VertxAgentServiceImpl(AgentConnections aConnections, Gson aGson, CommandResponses aCommandResponses, ITaskExecutionListener aListener) {
        this(aConnections, aGson, aCommandResponses, aListener, null);
    }

    public VertxAgentServiceImpl(AgentConnections aConnections, Gson aGson, CommandResponses aCommandResponses, ITaskExecutionListener aListener, AgentRegistry aRegistry) {
        connections = aConnections;
        gson = aGson;
        commandResponses = aCommandResponses;
        listener = aListener;
        registry = aRegistry;
    }

    @Override
    public void runCommand(RunAgentCommandRequest aCommand) throws AgentCommandException {
        checkAgentUnderstands(aCommand);
        LOG.debug("Sending command {} ", aCommand);
        listener.onSendingCommand(aCommand);
        try {
            sendMessage(aCommand.agentId, aCommand);
        } catch (InterruptedException e) {
            throw new AgentCommandException("Interrupted", e);
        }
        // waiting for response
        try {
            RunAgentCommandResponse response = commandResponses.awaitForCommandResponse(aCommand.commandId);
            listener.onCommandResponse(response);
            LOG.info("Response for command {} is {}", aCommand, response);
            if(response.error != null) {
                throw new AgentCommandException("Agent " + aCommand.agentId + ": return error: " + response.error, response.error);
            }
        } catch (InterruptedException e) {
            throw new AgentCommandException("Interrupted");
        }
    }


    /**
     * Refuses to send a step plan to an agent that has not said it can run one.
     *
     * <p>Without this the plan would be sent, the agent would try to start it as a program, and the deployment
     * would fail with a message about a missing file. Saying plainly that the agent is too old is what an
     * operator can act on.
     */
    private void checkAgentUnderstands(RunAgentCommandRequest aCommand) throws AgentCommandException {
        if (aCommand.command.type != AgentCommandType.STEPS || registry == null) {
            return;
        }
        if (!registry.hasCapability(aCommand.agentId, AgentInfo.CAPABILITY_STEPS_1)) {
            throw new AgentCommandException("Agent " + aCommand.agentId + " cannot run step plans: "
                    + registry.describe(aCommand.agentId)
                    + ". Update the agent, or install its policy file, before using an alias with steps.");
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
