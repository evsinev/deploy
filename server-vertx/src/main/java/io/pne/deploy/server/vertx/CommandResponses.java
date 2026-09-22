package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Waits for an agent to report that a command has finished.
 *
 * <p>The wait is extended while the agent keeps talking. A deployment that waits for a service to come back can
 * legitimately take longer than any fixed wait, and giving up on a working agent is the worst of both worlds:
 * the deployment is reported as failed while it is in fact still going on. An agent that has gone quiet is a
 * different matter, and that is what the shorter idle wait is for.
 */
public class CommandResponses {

    /** How long to wait with nothing at all heard from the agent. */
    public static final int WAIT_TIMEOUT_SECONDS = 60 * 10;

    /** How long a command may take in total, however talkative the agent is. */
    public static final int MAX_TOTAL_SECONDS = 60 * 60;

    private final Map<String, RunAgentCommandResponse> map      = new ConcurrentHashMap<>();
    private final Map<String, Long>                    lastHeard = new ConcurrentHashMap<>();

    private final int quietSeconds;
    private final int totalSeconds;

    public CommandResponses() {
        this(WAIT_TIMEOUT_SECONDS, MAX_TOTAL_SECONDS);
    }

    public CommandResponses(int aQuietSeconds, int aTotalSeconds) {
        quietSeconds = aQuietSeconds;
        totalSeconds = aTotalSeconds;
    }

    public RunAgentCommandResponse awaitForCommandResponse(String aCommandId) throws InterruptedException {
        long started = System.currentTimeMillis();

        try {
            while (true) {
                RunAgentCommandResponse response = map.get(aCommandId);
                if (response != null) {
                    return response;
                }

                long now   = System.currentTimeMillis();
                long quiet = (now - lastHeard.getOrDefault(aCommandId, started)) / 1000;
                long total = (now - started) / 1000;

                if (quiet >= quietSeconds) {
                    throw new IllegalStateException("No answer for command " + aCommandId + " and nothing heard"
                            + " from the agent for " + quiet + " seconds");
                }
                if (total >= totalSeconds) {
                    throw new IllegalStateException("Command " + aCommandId + " has been running for " + total
                            + " seconds, which is longer than any deployment should take");
                }

                Thread.sleep(1_000);
            }
        } finally {
            lastHeard.remove(aCommandId);
        }
    }

    /** Called when anything arrives from the agent about this command, which shows it is still working. */
    public void noteActivity(String aCommandId) {
        if (aCommandId != null) {
            lastHeard.put(aCommandId, System.currentTimeMillis());
        }
    }

    public void addCommandResponse(String aCommandId, RunAgentCommandResponse aResponse) {
        map.put(aCommandId, aResponse);
    }
}
