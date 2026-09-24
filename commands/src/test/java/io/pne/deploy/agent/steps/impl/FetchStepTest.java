package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepPlanExecutor;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.agent.steps.StepTestSupport;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FetchStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void downloadsAnArtifactIntoThePlannedLocation() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/artifact", 200, "artifact-body");
            Path target = root.resolve("staging/1.0");

            run(root, step("fetch", "url", server.url("/artifact"), "to", target.toString()));

            assertEquals("artifact-body", Files.readString(target, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void refusesAnUnexpectedStatusAndWritesNothing() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/missing", 404, "<html>not found</html>");
            Path target = root.resolve("staging/1.0");

            try {
                run(root, step("fetch", "url", server.url("/missing"), "to", target.toString()));
                fail("expected the download to fail");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("answered 404"));
            }

            assertFalse("an error page must never be stored as the artifact", Files.exists(target));
        }
    }

    @Test
    public void leavesNoPartialFileWhenTheDownloadIsTooLarge() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/artifact", 200, "0123456789");
            Path target = root.resolve("staging/1.0");

            StepPolicy policy = StepPolicy.builder()
                    .writeRoots(Collections.singletonList(root.toString()))
                    .fetchHosts(Collections.singletonList("127.0.0.1"))
                    .maxFetchBytes(4)
                    .build();

            try {
                new StepPlanExecutor(StepRegistry.defaults(), policy).run(
                        Collections.singletonList(step("fetch", "url", server.url("/artifact"), "to", target.toString())),
                        StepTestSupport.log());
                fail("expected the download to be refused");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("Refusing to read more than"));
            }

            assertFalse(Files.exists(target));
            assertNoLeftovers(root.resolve("staging"));
        }
    }

    @Test
    public void refusesAHostThePolicyDoesNotAllowWithoutSendingARequest() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(root.toString()))
                .fetchHosts(Collections.singletonList("artifacts.internal"))
                .build();

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy).run(
                    Collections.singletonList(step("fetch",
                            "url", "http://elsewhere.internal/artifact", "to", root.resolve("1.0").toString())),
                    StepTestSupport.log());
            fail("expected the plan to be refused");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("elsewhere.internal is not allowed"));
        }
    }

    private static void assertNoLeftovers(Path aDirectory) throws Exception {
        if (!Files.isDirectory(aDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.list(aDirectory)) {
            List<Path> left = files.toList();
            assertTrue("no partial files left behind, found " + left, left.isEmpty());
        }
    }

    private static void run(Path aRoot, io.pne.deploy.agent.api.command.AgentStep aStep)
            throws StepValidationException, StepExecutionException {
        new StepPlanExecutor(StepRegistry.defaults(), StepTestSupport.policyFor(aRoot))
                .run(Collections.singletonList(aStep), StepTestSupport.log());
    }
}
