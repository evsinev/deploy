package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An alias that declares the values it expects is turned into a plan of typed steps here, on the server, so the
 * whole plan can be seen and refused before anything reaches a host.
 */
public class StepAliasParserTest {

    private final AliasParser parser = new AliasParser(
              new File("src/test/resources/aliases")
            , new File("src/test/resources/recipes"));

    @Test
    public void buildsAPlanFromARecipe() throws Exception {
        Task task = parser.parseAlias("demo-service 1.2.3", -3);

        assertEquals(2, task.commands.size());

        TaskCommand first = task.commands.get(0);
        assertEquals("agent-1", first.agents.getIds()[0]);
        assertEquals(AgentCommandType.STEPS, first.command.type);

        List<AgentStep> steps = first.command.getSteps();
        assertEquals(7, steps.size());
        assertEquals("check-version", steps.get(0).getType());
        assertEquals("1.2.3", steps.get(0).getParams().get("version"));
    }

    @Test
    public void fillsInTheValuesOfARecipeIncludingItsDefaults() throws Exception {
        Task            task  = parser.parseAlias("demo-service 1.2.3", -3);
        List<AgentStep> steps = task.commands.get(0).command.getSteps();

        // The artifact source comes from the site file, the staging directory from a default built on another value.
        assertEquals("http://artifacts.example/artifacts/?artifact=demo-app&version=1.2.3",
                steps.get(1).getParams().get("url"));
        assertEquals("/srv/demo/staging/1.2.3", steps.get(1).getParams().get("to"));
        assertEquals("/srv/demo/staging/.VERSION", steps.get(2).getParams().get("path"));
        assertEquals("/service/demo", steps.get(4).getParams().get("service"));
    }

    @Test
    public void aValueLeftUnsetTakesItsDefault() throws Exception {
        Task            task  = parser.parseAlias("demo-service 1.2.3", -3);
        List<AgentStep> steps = task.commands.get(0).command.getSteps();

        assertEquals("0",  steps.get(3).getParams().get("seconds"));
        assertEquals("10", steps.get(5).getParams().get("seconds"));
    }

    @Test
    public void numbersInTheFileTravelAsText() throws Exception {
        Task            task  = parser.parseAlias("demo-service 1.2.3", -3);
        List<AgentStep> steps = task.commands.get(0).command.getSteps();

        assertEquals("180", steps.get(6).getParams().get("timeoutSeconds"));
    }

    @Test
    public void theDiffPointsAtTheDeclaredValueRatherThanACountedWord() throws Exception {
        Task task = parser.parseAlias("demo-service 1.2.3", -3);

        assertTrue(task.diff.isEnabled());
        assertEquals("the version is the first word after the alias", 1, task.diff.getNewVersionArg());
    }

    @Test
    public void valuesCanBeNamedByAKeywordOnTheLine() throws Exception {
        Task            task  = parser.parseAlias("demo-keyed app 1.2.3 assets 4.5.6", -3);
        List<AgentStep> steps = task.commands.get(0).command.getSteps();

        assertEquals("1.2.3", steps.get(0).getParams().get("content"));
        assertEquals("4.5.6", steps.get(1).getParams().get("content"));
    }

    @Test
    public void anOlderCommandAndAPlanCanLiveInTheSameAlias() throws Exception {
        Task task = parser.parseAlias("demo-mixed 1.2.3", -3);

        assertEquals(AgentCommandType.SHELL, task.commands.get(0).command.type);
        assertEquals("./bin/legacy-deploy.sh", task.commands.get(0).command.name);
        assertEquals(List.of("1.2.3"), task.commands.get(0).command.arguments);

        assertEquals(AgentCommandType.STEPS, task.commands.get(1).command.type);
    }

    @Test
    public void aMissingValueIsReportedWithAUsageLine() {
        try {
            parser.parseAlias("demo-service", -3);
            fail("expected the missing version to be reported");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("needs a value for 'version'"));
            assertTrue(e.getMessage(), e.getMessage().contains("Usage: demo-service <version>"));
        }
    }

    @Test
    public void aValueOfTheWrongShapeIsRefused() {
        try {
            parser.parseAlias("demo-service not-a-version", -3);
            fail("expected the value to be refused");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not a valid version"));
        }
    }

    @Test
    public void anExtraValueIsReportedRatherThanIgnored() {
        try {
            parser.parseAlias("demo-service 1.2.3 4.5.6", -3);
            fail("expected the extra value to be reported");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Too many values"));
        }
    }

    @Test
    public void thePlanCanBeWrittenOutWithoutRunningAnything() throws Exception {
        String plan = PlanRenderer.render(parser.parseAlias("demo-service 1.2.3", -3));

        assertTrue(plan, plan.contains("[1/2] agents=agent-1"));
        assertTrue(plan, plan.contains("1. check-version"));
        assertTrue(plan, plan.contains("version=1.2.3"));
        assertTrue(plan, plan.contains("5. signal-service"));
        assertTrue(plan, plan.contains("service=/service/demo"));
    }

    @Test
    public void anOlderAliasKeepsWorkingUnchanged() throws Exception {
        Task task = parser.parseAlias("proc 3.33-40", -3);

        assertEquals(AgentCommandType.SHELL, task.commands.get(0).command.type);
    }
}
