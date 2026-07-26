package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.exceptions.AgentCommandException;
import io.pne.deploy.agent.api.messages.RunAgentCommandRequest;
import io.pne.deploy.agent.service.log.IAgentLogService;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentServiceImplTest {

    private final List<String>      output     = Collections.synchronizedList(new ArrayList<>());
    private final IAgentLogService  logService = (id, text) -> output.add(text);
    private final AgentServiceImpl  service    = new AgentServiceImpl(logService);

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
