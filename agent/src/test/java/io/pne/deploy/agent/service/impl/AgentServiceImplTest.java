package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.service.log.IAgentLogService;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AgentServiceImplTest {

    private final List<String>      output     = Collections.synchronizedList(new ArrayList<>());
    private final IAgentLogService  logService = (id, text) -> output.add(text);
    private final AgentServiceImpl  service    = new AgentServiceImpl(logService);

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void addsExecuteBitAndRunsShScriptWithoutIt() throws Exception {
        File script = writeScript("#!/bin/sh\necho ok\n");
        assertTrue(script.setExecutable(false));            // simulate the bit stripped by deploy
        assertFalse("precondition: not executable", script.canExecute());

        service.runCommand(request(script.getAbsolutePath())); // must not throw (exit 0)

        assertTrue("execute bit added", script.canExecute());
        assertTrue("script output captured", waitForOutput("ok"));
    }

    @Test
    public void runsShScriptThatIsAlreadyExecutable() throws Exception {
        File script = writeScript("#!/bin/sh\necho ok\n");
        assertTrue(script.setExecutable(true));

        service.runCommand(request(script.getAbsolutePath())); // must not throw

        assertTrue(script.canExecute());
        assertTrue(waitForOutput("ok"));
    }

    @Test
    public void runsNonShCommandUnchanged() throws AgentCommandException, InterruptedException {
        AgentCommand echo = new AgentCommand(new AgentCommandParameters(), AgentCommandType.SHELL,
                "echo", Collections.singletonList("hello"));
        service.runCommand(new RunAgentCommandRequest("agent-1", "cmd-1", echo)); // must not throw
        assertTrue(waitForOutput("hello"));
    }

    @Test
    public void runsAStepPlanInsideTheAgentProcess() throws Exception {
        Path             root    = folder.getRoot().toPath().toRealPath();
        AgentServiceImpl service = new AgentServiceImpl(logService, policyFor(root));

        service.runCommand(stepsRequest(AgentCommand.ofSteps("demo", List.of(
                step("write-file", "path", root.resolve("version.txt").toString(), "content", "1.2.3")))));

        assertEquals("1.2.3" + System.lineSeparator(),
                Files.readString(root.resolve("version.txt"), StandardCharsets.UTF_8));
        assertTrue(waitForOutput("[1/1] write-file ok"));
    }

    @Test
    public void refusesAStepPlanWhenNoPolicyIsInstalled() {
        AgentCommand plan = AgentCommand.ofSteps("demo", List.of(step("sleep", "seconds", "0")));

        try {
            service.runCommand(stepsRequest(plan));
            fail("expected step plans to be refused without a policy");
        } catch (AgentCommandException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no policy file"));
        }
    }

    @Test
    public void refusesALegacyCommandWhenThePolicySwitchesItOff() throws Exception {
        Path             root    = folder.getRoot().toPath().toRealPath();
        StepPolicy       policy  = StepPolicy.builder().allowShell(false).build();
        AgentServiceImpl service = new AgentServiceImpl(logService, policy);

        try {
            service.runCommand(request("echo"));
            fail("expected the legacy command to be refused");
        } catch (AgentCommandException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("switched off"));
        }
    }

    @Test
    public void refusesALegacyCommandOutsideTheAllowedDirectory() {
        AgentServiceImpl service = new AgentServiceImpl(logService, policyWithCommandDirectory("./bin"));

        try {
            service.runCommand(request("/bin/sh"));
            fail("expected the command to be refused");
        } catch (AgentCommandException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the allowed directory"));
        }
    }

    @Test
    public void refusesALegacyCommandThatClimbsOutOfTheAllowedDirectory() {
        AgentServiceImpl service = new AgentServiceImpl(logService, policyWithCommandDirectory("./bin"));

        try {
            service.runCommand(request("./bin/../../../../bin/sh"));
            fail("a command must not be able to climb out of the allowed directory");
        } catch (AgentCommandException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not inside the allowed directory"));
        }
    }

    private static StepPolicy policyWithCommandDirectory(String aDirectory) {
        return StepPolicy.builder().allowShell(true).shellAllowedPrefix(aDirectory).build();
    }

    private static StepPolicy policyFor(Path aRoot) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .build();
    }

    private static AgentStep step(String aType, String... aKeyValuePairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < aKeyValuePairs.length; i += 2) {
            params.put(aKeyValuePairs[i], aKeyValuePairs[i + 1]);
        }
        return new AgentStep(aType, params);
    }

    private static RunAgentCommandRequest stepsRequest(AgentCommand aCommand) {
        return new RunAgentCommandRequest("agent-1", "cmd-1", aCommand);
    }

    private File writeScript(String content) throws Exception {
        File script = File.createTempFile("agent-service-test", ".sh");
        script.deleteOnExit();
        Files.writeString(script.toPath(), content, StandardCharsets.UTF_8);
        return script;
    }

    private static RunAgentCommandRequest request(String name) {
        AgentCommand command = new AgentCommand(new AgentCommandParameters(), AgentCommandType.SHELL,
                name, Collections.emptyList());
        return new RunAgentCommandRequest("agent-1", "cmd-1", command);
    }

    private boolean waitForOutput(String expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            synchronized (output) {
                for (String line : output) {
                    if (line.contains(expected)) {
                        return true;
                    }
                }
            }
            Thread.sleep(40);
        }
        return false;
    }
}
