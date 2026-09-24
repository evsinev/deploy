package io.pne.deploy.agent.steps.policy;

import io.pne.deploy.agent.steps.StepValidationException;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Turns a path from a plan into a real location and checks it against the policy.
 *
 * <p>Canonicalisation matters as much as the check itself, and it has to be done the way the operating system
 * does it: walking the path from the root and following every link on the way. A link planted inside an allowed
 * directory therefore cannot be used to reach outside it, including a link pointing at something that does not
 * exist yet, which would otherwise slip through unresolved and be followed later by the write itself.
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
     * The real location a path names.
     *
     * <p>The path is walked one name at a time from the root, following every symbolic link as it is met, and
     * {@code ..} is applied to what has been resolved so far. Collapsing {@code ..} beforehand, as text, would
     * give a different and wrong answer whenever a link sits earlier in the path - which is exactly the shape an
     * attacker would choose. The path itself does not have to exist; a step may well be creating it.
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
            path = Paths.get(text);
        } catch (InvalidPathException e) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' is not a valid path: " + e.getMessage());
        }

        return resolve(path, 0);
    }

    private static Path resolve(Path aPath, int aDepth) throws StepValidationException {
        if (aDepth > MAX_LINK_DEPTH) {
            throw new StepValidationException("Too many symbolic links while resolving " + aPath);
        }

        Path root   = aPath.getRoot();
        Path result = root;

        for (Path element : aPath) {
            String name = element.toString();
            if (".".equals(name)) {
                continue;
            }
            if ("..".equals(name)) {
                Path parent = result.getParent();
                result = parent == null ? root : parent;
                continue;
            }

            Path candidate = result.resolve(name);
            if (Files.isSymbolicLink(candidate)) {
                Path target = readLink(candidate);
                result = resolve(target.isAbsolute() ? target : result.resolve(target), aDepth + 1);
            } else {
                result = candidate;
            }
        }

        return result;
    }

    private static Path readLink(Path aLink) throws StepValidationException {
        try {
            return Files.readSymbolicLink(aLink);
        } catch (IOException e) {
            throw new StepValidationException("cannot read the symbolic link " + aLink + ": " + e.getMessage());
        }
    }

    private static StepValidationException refused(String aStepType, String aParam, Path aPath, String aProblem) {
        return new StepValidationException("step '" + aStepType + "', parameter '" + aParam + "': " + aPath + " " + aProblem);
    }
}
