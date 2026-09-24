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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.pne.deploy.agent.steps.StepTestSupport.step;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UnpackStepTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void unpacksIntoTheDestination() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path archive = zip(root.resolve("archive.zip"), Map.of("index.html", "page", "css/site.css", "body{}"));
        Path target  = root.resolve("www");

        run(root, step("unpack", "archive", archive.toString(), "to", target.toString()));

        assertEquals("page",  Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8));
        assertEquals("body{}", Files.readString(target.resolve("css/site.css"), StandardCharsets.UTF_8));
    }

    @Test
    public void replaceModeClearsWhatWasThereBefore() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Files.writeString(target.resolve("stale.html"), "old");
        Path archive = zip(root.resolve("archive.zip"), Map.of("index.html", "page"));

        run(root, step("unpack", "archive", archive.toString(), "to", target.toString(), "mode", "replace"));

        assertTrue(Files.exists(target.resolve("index.html")));
        assertFalse("the previous contents must be gone", Files.exists(target.resolve("stale.html")));
    }

    @Test
    public void atomicReplaceSwapsTheDirectoryAndLeavesNothingBehind() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Files.writeString(target.resolve("stale.html"), "old");
        Path archive = zip(root.resolve("archive.zip"), Map.of("index.html", "page"));

        run(root, step("unpack", "archive", archive.toString(), "to", target.toString(), "mode", "atomic-replace"));

        assertEquals("page", Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(target.resolve("stale.html")));
        try (var files = Files.list(root)) {
            assertEquals("only the archive and the destination remain", 2, files.count());
        }
    }

    @Test
    public void refusesAnEntryThatWouldBeWrittenOutsideTheDestination() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path archive = zip(root.resolve("archive.zip"), Map.of("../escaped.txt", "nope"));
        Path target  = root.resolve("www");

        try {
            run(root, step("unpack", "archive", archive.toString(), "to", target.toString()));
            fail("expected the escaping entry to be refused");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("would be written outside"));
        }

        assertFalse(Files.exists(root.resolve("escaped.txt")));
    }

    @Test
    public void refusesAnEntryThatWouldFollowALinkAlreadyInTheDestination() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.createSymbolicLink(target.resolve("config"), outside);

        Path archive = zip(root.resolve("archive.zip"), Map.of("config/payload.txt", "nope"));

        try {
            run(root, step("unpack", "archive", archive.toString(), "to", target.toString()));
            fail("expected the planted link to be seen through");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("would be written outside"));
        }

        assertFalse(Files.exists(outside.resolve("payload.txt")));
    }

    @Test
    public void replacesALinkAtAnEntryRatherThanWritingThroughIt() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Path outside = root.resolve("outside.txt");
        Files.writeString(outside, "untouched");
        Files.createSymbolicLink(target.resolve("index.html"), outside);

        Path archive = zip(root.resolve("archive.zip"), Map.of("index.html", "page"));

        run(root, step("unpack", "archive", archive.toString(), "to", target.toString()));

        assertEquals("page", Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8));
        assertEquals("untouched", Files.readString(outside, StandardCharsets.UTF_8));
    }

    @Test
    public void leavesThePreviousFileAloneWhenAnEntryIsTooLarge() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Files.writeString(target.resolve("index.html"), "previous");
        Path archive = zip(root.resolve("archive.zip"), Map.of("index.html", "x".repeat(100)));

        StepPolicy policy = StepPolicy.builder()
                .writeRoots(Collections.singletonList(root.toString()))
                .maxUnpackBytes(10)
                .build();

        try {
            new StepPlanExecutor(StepRegistry.defaults(), policy).run(
                    Collections.singletonList(
                            step("unpack", "archive", archive.toString(), "to", target.toString())),
                    StepTestSupport.log());
            fail("expected the oversized entry to be refused");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Refusing to read more than"));
        }

        assertEquals("the previous file must survive a failed unpack",
                "previous", Files.readString(target.resolve("index.html"), StandardCharsets.UTF_8));
    }

    @Test
    public void refusesSomethingThatIsNotAnArchiveInsteadOfEmptyingTheDestination() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Files.writeString(target.resolve("index.html"), "previous");
        Path archive = root.resolve("archive.zip");
        Files.writeString(archive, "not a zip at all");

        try {
            run(root, step("unpack", "archive", archive.toString(), "to", target.toString(), "mode", "replace"));
            fail("expected an archive with no entries to be refused");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("contains no entries"));
        }
    }

    @Test
    public void doesNotCreateDirectoriesBeyondAPlantedLink() throws Exception {
        Path root    = folder.getRoot().toPath().toRealPath();
        Path target  = Files.createDirectories(root.resolve("www"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.createSymbolicLink(target.resolve("config"), outside);

        Path archive = zip(root.resolve("archive.zip"), Map.of("config/nested/deep/payload.txt", "nope"));

        try {
            run(root, step("unpack", "archive", archive.toString(), "to", target.toString()));
            fail("expected the planted link to be seen through");
        } catch (StepExecutionException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("would be written outside"));
        }

        assertFalse("nothing may be created on the far side of the link",
                Files.exists(outside.resolve("nested")));
    }

    private static Path zip(Path aPath, Map<String, String> aEntries) throws Exception {
        try (OutputStream out = Files.newOutputStream(aPath); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : aEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return aPath;
    }

    private static void run(Path aRoot, AgentStep aStep) throws StepValidationException, StepExecutionException {
        new StepPlanExecutor(StepRegistry.defaults(), StepTestSupport.policyFor(aRoot))
                .run(Collections.singletonList(aStep), StepTestSupport.log());
    }
}
