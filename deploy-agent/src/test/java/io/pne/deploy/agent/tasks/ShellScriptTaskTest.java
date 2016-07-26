package io.pne.deploy.agent.tasks;

import com.google.common.collect.ImmutableMap;
import io.pne.deploy.agent.tasks.shellscript.ShellScriptTask;
import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.*;

import java.io.File;
import java.util.UUID;

public class ShellScriptTaskTest {

    @Test
    public void test_sh() {

        ShellScriptTask task = new ShellScriptTask(new File("./src/test/resources/scripts"));

        ITaskContext context = mock(ITaskContext.class);

        ImmutableShellScriptParameters parameters = ImmutableShellScriptParameters.builder()
                .taskId(UUID.randomUUID().toString())
                .username(ShellScriptParameters.USERNAME_NON_ROOT)
                .filename("test.sh")
                .arguments(new String[]{"test.sh"})
                .environment(ImmutableMap.of("ENV_VARIABLE", "env-value"))
                .build();

        ShellScriptResult result = task.execute(context, parameters);
        Assert.assertEquals(0, result.exitCode());

        InOrder inOrder = inOrder(context);
        inOrder.verify(context).log("ARGUMENT_1=test.sh");
        inOrder.verify(context).log("ENV_VARIABLE=env-value");
        inOrder.verify(context).log("ERROR OUTPUT");
        inOrder.verify(context).log("STD OUTPUT");

    }
}
