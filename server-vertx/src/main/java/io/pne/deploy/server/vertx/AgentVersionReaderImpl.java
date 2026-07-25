package io.pne.deploy.server.vertx;

import com.google.gson.Gson;
import io.pne.deploy.agent.api.messages.AgentMessageType;
import io.pne.deploy.agent.api.messages.GetVersionRequest;
import io.pne.deploy.agent.api.messages.GetVersionResponse;
import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.server.api.IAgentVersionReader;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Asks {@code agentId} to fetch {@code versionUrl} itself (HttpURLConnection) and returns the version. */
public class AgentVersionReaderImpl implements IAgentVersionReader {

    private static final Logger LOG = LoggerFactory.getLogger(AgentVersionReaderImpl.class);

    private final AgentConnections connections;
    private final Gson             gson;
    private final VersionResponses versionResponses;

    public AgentVersionReaderImpl(AgentConnections aConnections, Gson aGson, VersionResponses aVersionResponses) {
        this.connections      = aConnections;
        this.gson             = aGson;
        this.versionResponses = aVersionResponses;
    }

    @Override
    public String readVersion(String aAgentId, String aVersionUrl) {
        if (aAgentId == null || aAgentId.isBlank() || aVersionUrl == null || aVersionUrl.isBlank()) {
            LOG.warn("readVersion: blank agent '{}' or url '{}'", aAgentId, aVersionUrl);
            return null;
        }
        String requestId = "getversion-" + UUID.randomUUID();
        try {
            ServerWebSocket socket = connections.getSocket(aAgentId);
            LOG.info("Asking agent {} for the version at {} (request {})", aAgentId, aVersionUrl, requestId);
            socket.writeBinaryMessage(frame(new GetVersionRequest(requestId, aVersionUrl)));
            GetVersionResponse response = versionResponses.awaitForVersionResponse(requestId);
            if (response.error != null) {
                LOG.warn("Agent {} couldn't read version from {}: {}", aAgentId, aVersionUrl, response.error);
                return null;
            }
            return response.version;
        } catch (Exception e) {
            LOG.warn("Can't read version via agent {} from {}", aAgentId, aVersionUrl, e);
            return null;
        }
    }

    private Buffer frame(IAgentServerMessage aMessage) {
        Buffer buffer = Buffer.buffer();
        buffer.appendByte((byte) 0x01);
        buffer.appendByte(AgentMessageType.findByClass(aMessage.getClass()).id);
        buffer.appendBytes(gson.toJson(aMessage).getBytes(StandardCharsets.UTF_8));
        return buffer;
    }
}
