package io.pne.deploy.agent.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.api.messages.AgentInfo;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The wire contract between a server and an agent that may be a release apart.
 *
 * <p>These tests pin the behaviour the rollout order depends on: an older agent must still understand what a newer
 * server sends, and a newer agent must still understand what an older server sends.
 */
public class AgentMessageGsonTest {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    public void aStepPlanSurvivesTheRoundTrip() {
        AgentCommand command = AgentCommand.ofSteps("demo", List.of(
                new AgentStep("fetch", Map.of("url", "http://artifacts.internal/app", "to", "/srv/apps/staging/1.0")),
                new AgentStep("sleep", Map.of("seconds", "10"))));

        RunAgentCommandRequest restored = roundTrip(new RunAgentCommandRequest("agent-1", "cmd-1", command));

        assertEquals(AgentCommandType.STEPS, restored.command.type);
        assertEquals(2, restored.command.getSteps().size());
        assertEquals("fetch", restored.command.getSteps().get(0).getType());
        assertEquals("/srv/apps/staging/1.0", restored.command.getSteps().get(0).getParams().get("to"));
        assertEquals("10", restored.command.getSteps().get(1).getParams().get("seconds"));
    }

    @Test
    public void aRequestFromAnOlderServerStillParses() {
        String json = "{'agentId':'agent-1','commandId':'cmd-1','command':{"
                + "'parameters':{},'type':'SHELL','name':'./bin/deploy.sh','arguments':['1.2.3']}}";

        RunAgentCommandRequest request = gson.fromJson(json.replace('\'', '"'), RunAgentCommandRequest.class);

        assertEquals(AgentCommandType.SHELL, request.command.type);
        assertEquals("./bin/deploy.sh", request.command.name);
        assertNull("an older server sends no steps", request.command.steps);
        assertTrue(request.command.getSteps().isEmpty());
    }

    @Test
    public void aCommandTypeThisBuildDoesNotKnowArrivesAsNull() {
        String json = "{'agentId':'agent-1','commandId':'cmd-1','command':{"
                + "'parameters':{},'type':'SOMETHING_NEWER','name':'x','arguments':[]}}";

        RunAgentCommandRequest request = gson.fromJson(json.replace('\'', '"'), RunAgentCommandRequest.class);

        assertNull("the agent must notice it cannot run this, not guess", request.command.type);
    }

    @Test
    public void thePlanNameCannotBeMistakenForAProgram() {
        AgentCommand command = AgentCommand.ofSteps("demo", List.of(new AgentStep("sleep", Map.of("seconds", "0"))));

        assertTrue(command.name.startsWith(AgentCommand.STEPS_NAME_PREFIX));
        // A name containing a separator is a path, so an older agent looks for that exact file and fails,
        // instead of searching the directories it would search for a bare program name.
        assertTrue("the plan name must be a path, not a program name", command.name.contains("/"));
        assertTrue(command.arguments.isEmpty());
    }

    @Test
    public void agentInfoFromAnOlderAgentHasNoCapabilities() {
        AgentInfo info = gson.fromJson("{\"version\":\"1.0-1\",\"heapUsed\":1,\"heapMax\":2}", AgentInfo.class);

        assertNull(info.capabilities);
        assertTrue(info.getCapabilities().isEmpty());
        assertFalse(info.hasCapability(AgentInfo.CAPABILITY_STEPS_1));
    }

    @Test
    public void agentInfoCarriesCapabilitiesBothWays() {
        AgentInfo info = gson.fromJson(gson.toJson(
                new AgentInfo("1.0-2", 1, 2, List.of(AgentInfo.CAPABILITY_STEPS_1))), AgentInfo.class);

        assertTrue(info.hasCapability(AgentInfo.CAPABILITY_STEPS_1));
    }

    private RunAgentCommandRequest roundTrip(RunAgentCommandRequest aRequest) {
        return gson.fromJson(gson.toJson(aRequest), RunAgentCommandRequest.class);
    }
}
