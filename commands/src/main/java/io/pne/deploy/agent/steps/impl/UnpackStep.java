package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.PathGuard;
import io.pne.deploy.agent.steps.policy.StepPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unpacks a zip archive into a directory.
 *
 * <p>Three modes cover what deployments actually do: {@code merge} writes over whatever is there, {@code replace}
 * empties the directory first, and {@code atomic-replace} builds the new contents beside the directory and swaps
 * it in with two renames, which is as close to a swap as a directory allows.
 *
 * <p>An entry that would land outside the destination is refused, and that is checked twice: once on the name,
 * which catches {@code ..}, and once on the real location of the directory the entry would go into, which catches
 * a symbolic link already sitting in the destination and pointing somewhere else.
 */
public class UnpackStep implements IStep {

    public static final String TYPE = "unpack";

    public static final String MODE_MERGE          = "merge";
    public static final String MODE_REPLACE        = "replace";
    public static final String MODE_ATOMIC_REPLACE = "atomic-replace";

    private static final List<String> MODES = Arrays.asList(MODE_MERGE, MODE_REPLACE, MODE_ATOMIC_REPLACE);

    private final String archive;
    private final String to;
    private final String mode;

    public UnpackStep(StepParams aParams) throws StepValidationException {
        archive = aParams.required("archive");
        to      = aParams.required("to");
        mode    = aParams.optional("mode", MODE_MERGE);
        if (!MODES.contains(mode)) {
            throw aParams.error("mode", "must be one of " + MODES + ", got '" + mode + "'");
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "archive", archive);
        aScope.checkReferences(TYPE, "to", to);
        PathGuard.checkReadable(aPolicy, TYPE, "archive", StepPlanScope.withPlaceholders(archive, "0"));

        String target = StepPlanScope.withPlaceholders(to, "0");
        if (MODE_MERGE.equals(mode)) {
            PathGuard.checkWritable(aPolicy, TYPE, "to", target);
        } else {
            PathGuard.checkDeletable(aPolicy, TYPE, "to", target);
        }
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        StepPolicy policy = aContext.getPolicy();
        try {
            Path source = PathGuard.checkReadable(policy, TYPE, "archive", aContext.expand(archive));
            String expandedTo = aContext.expand(to);
            Path target = MODE_MERGE.equals(mode)
                    ? PathGuard.checkWritable(policy, TYPE, "to", expandedTo)
                    : PathGuard.checkDeletable(policy, TYPE, "to", expandedTo);

            if (!Files.isRegularFile(source)) {
                throw new StepExecutionException("Not a file: " + source);
            }

            switch (mode) {
                case MODE_MERGE:
                    unpackInto(aContext, source, target, target);
                    break;

                case MODE_REPLACE:
                    FileOperations.deleteRecursively(target);
                    unpackInto(aContext, source, target, target);
                    break;

                default:
                    atomicReplace(aContext, source, target);
                    break;
            }

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot unpack " + archive + " into " + to + ": " + e, e);
        }
    }

    private void unpackInto(StepContext aContext, Path aArchive, Path aTarget, Path aBoundary) throws IOException, StepExecutionException {
        StepPolicy policy = aContext.getPolicy();
        Files.createDirectories(aTarget);
        Path boundary = aBoundary.toRealPath();

        long entries = 0;
        long bytes   = 0;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(aArchive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > policy.getMaxUnpackEntries()) {
                    throw new StepExecutionException("Archive " + aArchive + " has more than "
                            + policy.getMaxUnpackEntries() + " entries");
                }

                Path resolved = boundary.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(boundary)) {
                    throw new StepExecutionException(outsideMessage(aArchive, boundary, entry.getName()));
                }

                if (entry.isDirectory()) {
                    createDirectoryInside(aArchive, boundary, resolved, entry.getName());
                    continue;
                }

                createDirectoryInside(aArchive, boundary, resolved.getParent(), entry.getName());
                bytes += writeEntry(aArchive, boundary, zip, resolved, entry.getName(),
                        policy.getMaxUnpackBytes() - bytes);
            }
        }

        aContext.log("unpacked " + entries + " entry(ies), " + bytes + " byte(s) into " + aTarget);
    }

    /**
     * Creates a directory of the archive and checks where it really ended up. A link already present in the
     * destination would otherwise redirect everything written below it.
     */
    private static void createDirectoryInside(Path aArchive, Path aBoundary, Path aDirectory, String aEntryName)
            throws IOException, StepExecutionException {
        Files.createDirectories(aDirectory);
        if (!aDirectory.toRealPath().startsWith(aBoundary)) {
            throw new StepExecutionException(outsideMessage(aArchive, aBoundary, aEntryName));
        }
    }

    /**
     * Writes one entry beside its destination and moves it into place, so an entry that turns out to be too large
     * or truncated does not leave the previous file half overwritten. An existing link at the destination is
     * removed rather than written through.
     */
    private static long writeEntry(
              Path             aArchive
            , Path             aBoundary
            , InputStream      aZip
            , Path             aTarget
            , String           aEntryName
            , long             aRemainingBytes
    ) throws IOException, StepExecutionException {

        if (aRemainingBytes <= 0) {
            throw new IOException("Refusing to unpack more bytes than the policy allows");
        }
        if (Files.isSymbolicLink(aTarget)) {
            Files.delete(aTarget);
        }
        if (Files.exists(aTarget, LinkOption.NOFOLLOW_LINKS) && !aTarget.toRealPath().startsWith(aBoundary)) {
            throw new StepExecutionException(outsideMessage(aArchive, aBoundary, aEntryName));
        }

        Path temp = FileOperations.temporarySibling(aTarget);
        try {
            long written;
            try (OutputStream out = Files.newOutputStream(temp)) {
                written = FileOperations.copyLimited(aZip, out, aRemainingBytes);
            }
            FileOperations.moveInto(temp, aTarget);
            return written;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String outsideMessage(Path aArchive, Path aBoundary, String aEntryName) {
        return "Archive " + aArchive + " contains an entry that would be written outside " + aBoundary
                + ": " + aEntryName;
    }

    /**
     * Builds the new contents next to the destination and swaps them in. Two renames are the closest a directory
     * gets to an atomic replacement, so there is a short moment when the destination is missing; if the second
     * rename fails, the previous contents are put back rather than left aside under another name.
     */
    private void atomicReplace(StepContext aContext, Path aSource, Path aTarget) throws IOException, StepExecutionException {
        long stamp    = System.nanoTime();
        Path staging  = aTarget.resolveSibling(aTarget.getFileName() + ".new-" + stamp);
        Path previous = aTarget.resolveSibling(aTarget.getFileName() + ".old-" + stamp);

        try {
            unpackInto(aContext, aSource, staging, staging);

            boolean hadPrevious = Files.exists(aTarget, LinkOption.NOFOLLOW_LINKS);
            if (hadPrevious) {
                FileOperations.moveInto(aTarget, previous);
            }
            try {
                FileOperations.moveInto(staging, aTarget);
            } catch (IOException e) {
                if (hadPrevious) {
                    FileOperations.moveInto(previous, aTarget);
                }
                throw e;
            }
            if (hadPrevious) {
                FileOperations.deleteRecursively(previous);
            }
        } finally {
            FileOperations.deleteRecursively(staging);
        }
    }

    @Override
    public long getMaxSeconds() {
        return 60;
    }
}
