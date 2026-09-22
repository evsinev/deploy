package io.pne.deploy.agent.steps.policy;

import io.pne.deploy.agent.steps.StepValidationException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Turns a path from a plan into a real location and checks it against the policy.
 *
 * <p>Canonicalisation matters as much as the check itself: the path is made absolute, {@code ..} is collapsed, and
 * symbolic links are followed - including a link that points at something which does not exist yet, which would
 * otherwise slip through unresolved and be followed later by the write itself. A link planted inside an allowed
 * directory therefore cannot be used to reach outside it.
 *
 * <p>What this cannot defend against is another process on the same host swapping a directory for a link between
 * the check and the write. Java offers no way to pin a directory across those two operations, so the roots must
 * only ever contain directories that nothing untrusted can write to.
 */
public class PathGuard {

    private static final int MAX_LINK_DEPTH = 16;

    private PathGuard() {
    }

    public static Path checkWritable(StepPolicy aPolicy, String aStepType, String aParam, String aPath) throws StepValidationException {
        Path path = canonicalize(aStepType, aParam, aPath);
        if (!aPolicy.getWriteRoots().allows(path)) {
            throw refused(aStepType, aParam, path, "is not inside the writable roots " + aPolicy.getWriteRoots());
        }
        return path;
    }

    public static Path checkReadable(StepPolicy aPolicy, String aStepType, String aParam, String aPath) throws StepValidationException {
        Path path = canonicalize(aStepType, aParam, aPath);
        if (!aPolicy.getReadRoots().allows(path)) {
            throw refused(aStepType, aParam, path, "is not inside the readable roots " + aPolicy.getReadRoots());
        }
        return path;
    }

    public static Path checkDeletable(StepPolicy aPolicy, String aStepType, String aParam, String aPath) throws StepValidationException {
        Path path = canonicalize(aStepType, aParam, aPath);
        if (!aPolicy.getWriteRoots().allowsDelete(path)) {
            throw refused(aStepType, aParam, path,
                    "may not be deleted; it must be inside the writable roots " + aPolicy.getWriteRoots()
                            + " and never the top of one");
        }
        return path;
    }

    public static Path checkServiceDir(StepPolicy aPolicy, String aStepType, String aParam, String aPath) throws StepValidationException {
        Path path = canonicalize(aStepType, aParam, aPath);
        if (!aPolicy.getServiceDirs().allows(path)) {
            throw refused(aStepType, aParam, path, "is not one of the service directories " + aPolicy.getServiceDirs());
        }
        return path;
    }

    /**
     * Absolute, {@code ..}-free, with the existing part resolved through symbolic links. The path itself does not
     * have to exist - a step may well be creating it.
     */
    public static Path canonicalize(String aStepType, String aParam, String aPath) throws StepValidationException {
        if (aPath == null || aPath.trim().isEmpty()) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam + "' is required");
        }
        String text = aPath.trim();
        if (!text.startsWith("/")) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' must be an absolute path, got '" + text + "'");
        }

        Path path;
        try {
            path = Paths.get(text).normalize();
        } catch (InvalidPathException e) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' is not a valid path: " + e.getMessage());
        }

        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                        + "' must not contain '..', got '" + text + "'");
            }
        }

        return resolveExistingPart(path);
    }

    private static Path resolveExistingPart(Path aPath) throws StepValidationException {
        return resolveExistingPart(aPath, 0);
    }

    private static Path resolveExistingPart(Path aPath, int aDepth) throws StepValidationException {
        if (aDepth > MAX_LINK_DEPTH) {
            throw new StepValidationException("Too many symbolic links while resolving " + aPath);
        }

        // NOFOLLOW_LINKS on purpose: a link pointing at something that does not exist yet still exists itself,
        // and has to be resolved here rather than left for the write to follow.
        Path existing = aPath;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return aPath;
        }

        if (Files.isSymbolicLink(existing)) {
            Path target = readLinkTarget(existing);
            Path merged = existing.equals(aPath) ? target : target.resolve(existing.relativize(aPath));
            return resolveExistingPart(merged.normalize(), aDepth + 1);
        }

        try {
            Path real = existing.toRealPath();
            return existing.equals(aPath) ? real : real.resolve(existing.relativize(aPath));
        } catch (IOException e) {
            throw new StepValidationException("cannot resolve path " + aPath + ": " + e.getMessage());
        }
    }

    private static Path readLinkTarget(Path aLink) throws StepValidationException {
        try {
            Path target = Files.readSymbolicLink(aLink);
            return target.isAbsolute() ? target.normalize() : aLink.getParent().resolve(target).normalize();
        } catch (IOException e) {
            throw new StepValidationException("cannot read the symbolic link " + aLink + ": " + e.getMessage());
        }
    }

    private static StepValidationException refused(String aStepType, String aParam, Path aPath, String aProblem) {
        return new StepValidationException("step '" + aStepType + "', parameter '" + aParam + "': " + aPath + " " + aProblem);
    }
}
