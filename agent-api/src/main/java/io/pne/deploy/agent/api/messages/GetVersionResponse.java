package io.pne.deploy.agent.api.messages;

/** Agent -> server: the fetched version (or {@code error} when the agent couldn't read it). */
public class GetVersionResponse implements IAgentClientMessage {

    public final String requestId;
    public final String version;
    public final String error;

    public GetVersionResponse(String requestId, String version, String error) {
        this.requestId = requestId;
        this.version   = version;
        this.error     = error;
    }

    @Override
    public String toString() {
        return "GetVersionResponse{requestId='" + requestId + "', version='" + version + "', error='" + error + "'}";
    }
}
