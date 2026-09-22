package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.api.command.AgentStep;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The steps that read a version over HTTP: refusing a downgrade, waiting for a restart, resolving a version. */
public class VersionStepsTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void checkVersionAcceptsANewerVersion() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/version", 200, "1.2.3");

            run(step("check-version", "url", server.url("/version"), "version", "1.2.4"));
        }
    }

    @Test
    public void checkVersionRefusesADowngrade() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/version", 200, "1.2.3");

            try {
                run(step("check-version", "url", server.url("/version"), "version", "1.2.2"));
                fail("expected the downgrade to be refused");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("must be greater than or equal"));
            }
        }
    }

    @Test
    public void waitUrlSucceedsOnceTheApplicationReportsTheNewVersion() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respondInTurn("/version", "1.2.4");

            run(step("wait-url", "url", server.url("/version"), "version", "1.2.4", "timeoutSeconds", "5"));
        }
    }

    @Test
    public void waitUrlFailsFastWhenAnotherVersionAnswers() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/version", 200, "1.2.3");

            long started = System.currentTimeMillis();
            try {
                run(step("wait-url", "url", server.url("/version"), "version", "1.2.4", "timeoutSeconds", "30"));
                fail("expected the wrong version to fail the step");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("reports 1.2.3"));
            }
            assertTrue("it must not wait out the timeout", System.currentTimeMillis() - started < 10_000);
        }
    }

    @Test
    public void waitUrlKeepsWaitingForAnotherVersionWhenAskedTo() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respondInTurn("/version", "1.2.3", "1.2.4");

            run(step("wait-url", "url", server.url("/version"), "version", "1.2.4",
                    "timeoutSeconds", "10", "failOnOtherVersion", "false"));
        }
    }

    @Test
    public void resolveVersionMakesTheValueAvailableToLaterSteps() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/wanted", 200, "2.0.1");

            run(root, Arrays.asList(
                    step("resolve-version", "url", server.url("/wanted"), "var", "wanted"),
                    step("write-file", "path", root.resolve("version.txt").toString(), "content", "${wanted}")));

            assertEquals("2.0.1" + System.lineSeparator(),
                    Files.readString(root.resolve("version.txt"), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void resolveVersionRefusesAnAnswerThatIsNotAVersion() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.respond("/wanted", 200, "../../etc");

            try {
                run(step("resolve-version", "url", server.url("/wanted"), "var", "wanted"));
                fail("expected the answer to be refused");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("does not match"));
            }
        }
    }

    @Test
    public void aVariableNoEarlierStepDeclaresIsReportedBeforeAnythingRuns() throws Exception {
        Path root = folder.getRoot().toPath().toRealPath();

        try {
            run(root, Collections.singletonList(
                    step("write-file", "path", root.resolve("version.txt").toString(), "content", "${missing}")));
            fail("expected the unknown variable to be reported");
        } catch (StepValidationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("unknown variable ${missing}"));
        }
    }

    @Test
    public void aRedirectIsNotFollowedToAHostNobodyAllowed() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/version", "http://169.254.169.254/latest/meta-data/");

            try {
                run(step("check-version", "url", server.url("/version"), "version", "1.2.4"));
                fail("expected the redirect to be refused rather than followed");
            } catch (StepExecutionException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("answered 302"));
            }
        }
    }

    private void run(AgentStep aStep) throws Exception {
        run(folder.getRoot().toPath().toRealPath(), Collections.singletonList(aStep));
    }

    private void run(Path aRoot, List<AgentStep> aSteps) throws StepValidationException, StepExecutionException {
        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toString()))
                .statusHosts(Collections.singletonList("127.0.0.1"))
                .build();
        new StepPlanExecutor(StepRegistry.defaults(), policy).run(aSteps, StepTestSupport.log());
    }
}
