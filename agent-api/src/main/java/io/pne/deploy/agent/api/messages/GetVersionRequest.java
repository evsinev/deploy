package io.pne.deploy.agent.api.messages;

/** Server -> agent: fetch {@code versionUrl} from where the agent (not the firewalled server) can reach it. */
public class GetVersionRequest implements IAgentServerMessage {

    public final String requestId;
    public final String versionUrl;

    public GetVersionRequest(String requestId, String versionUrl) {
        this.requestId  = requestId;
        this.versionUrl = versionUrl;
    }

    @Override
    public String toString() {
        return "GetVersionRequest{requestId='" + requestId + "', versionUrl='" + versionUrl + "'}";
    }
}
