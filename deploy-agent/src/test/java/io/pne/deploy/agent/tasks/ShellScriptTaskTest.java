package io.pne.deploy.agent.tasks;

import com.google.common.collect.ImmutableMap;
import io.pne.deploy.agent.tasks.shellscript.ShellScriptTask;
import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ShellScriptTaskTest {


    @Test
    public void test_sh() throws InterruptedException {

        ShellScriptTask task = new ShellScriptTask(new File("./src/test/resources/scripts"));

        ImmutableShellScriptParameters parameters = ImmutableShellScriptParameters.builder()
                .taskId(UUID.randomUUID().toString())
                .username(ShellScriptParameters.USERNAME_NON_ROOT)
                .filename("test.sh")
                .arguments(new String[]{"test.sh"})
                .environment(ImmutableMap.of("ENV_VARIABLE", "env-value"))
                .build();

        TestContext context = new TestContext();
        ShellScriptResult result = task.execute(context, parameters);
        Assert.assertEquals(0, result.exitCode());

        Assert.assertTrue("ITaskContext.log() must be invoked 4 times", context.logsLatch.await(2, TimeUnit.SECONDS));

        Assert.assertEquals("ARGUMENT_1=test.sh"    , context.logs.get(0));
        Assert.assertEquals("ENV_VARIABLE=env-value", context.logs.get(1));
        Assert.assertEquals("ERROR OUTPUT"          , context.logs.get(2));
        Assert.assertEquals("STD OUTPUT"            , context.logs.get(3));

        Assert.assertNull(context.result);

    }

    public static class TestContext implements ITaskContext {
        List<String> logs = new ArrayList<>();
        ShellScriptResult result;
        CountDownLatch logsLatch = new CountDownLatch(4);

        @Override
        public void log(String aLine) {
            logs.add(aLine);
            logsLatch.countDown();
        }

        @Override
        public void sendResultToServer(ShellScriptResult aResult) {
            result = aResult;
        }
    }
}
