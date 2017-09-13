package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommandResponses {

    public static final int WAIT_TIMEOUT_SECONDS = 60 * 10;
    
    private Map<String, RunAgentCommandResponse> map;

    public CommandResponses() {
        map = new ConcurrentHashMap<>();
    }

    public RunAgentCommandResponse awaitForCommandResponse(String aCommandId) throws InterruptedException {
        for(int i = 0; i< WAIT_TIMEOUT_SECONDS; i++) {
            RunAgentCommandResponse response = map.get(aCommandId);
            if(response != null) {
                return response;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("Could not get response for command within " + aCommandId + " within " + WAIT_TIMEOUT_SECONDS + " seconds");
    }

    public void addCommandResponse(String aCommandId, RunAgentCommandResponse aResponse) {
        map.put(aCommandId, aResponse);
    }
}
