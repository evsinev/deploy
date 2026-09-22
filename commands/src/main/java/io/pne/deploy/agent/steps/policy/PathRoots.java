package io.pne.deploy.agent.steps.policy;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of absolute locations a plan is allowed to touch.
 *
 * <p>An entry without a wildcard is a directory prefix: it allows that directory and everything below it. An entry
 * with a wildcard is a glob matched against the whole path, where a single star stays inside one name and a double
 * star spans names - so a pattern naming the staging directory of any application allows those and nothing else.
 */
public class PathRoots {

    private final List<String>      patterns;
    private final List<PathMatcher> matchers;
    /** For each pattern, the leading part with no wildcard - the highest directory the pattern can reach. */
    private final List<Path>        literalPrefixes;

    public PathRoots(List<String> aPatterns) {
        patterns        = new ArrayList<>();
        matchers        = new ArrayList<>();
        literalPrefixes = new ArrayList<>();

        for (String pattern : aPatterns) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("/")) {
                throw new IllegalArgumentException("Path root must be absolute: " + trimmed);
            }
            patterns.add(trimmed);
            matchers.add(hasWildcard(trimmed)
                    ? FileSystems.getDefault().getPathMatcher("glob:" + trimmed)
                    : null);
            literalPrefixes.add(literalPrefixOf(trimmed));
        }
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    public List<String> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    /** True when the path is inside one of the roots (or is a root itself). */
    public boolean allows(Path aPath) {
        for (int i = 0; i < patterns.size(); i++) {
            PathMatcher matcher = matchers.get(i);
            if (matcher == null) {
                Path prefix = literalPrefixes.get(i);
                if (aPath.equals(prefix) || aPath.startsWith(prefix)) {
                    return true;
                }
            } else if (matcher.matches(aPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the path may be deleted: it must be inside a root and must never be the top of one, so that a plan
     * can replace a directory it owns but cannot wipe a whole root.
     */
    public boolean allowsDelete(Path aPath) {
        if (!allows(aPath)) {
            return false;
        }
        if (aPath.getNameCount() < 2) {
            return false;
        }
        for (Path prefix : literalPrefixes) {
            if (aPath.equals(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return patterns.toString();
    }

    private static boolean hasWildcard(String aPattern) {
        return aPattern.indexOf('*') >= 0 || aPattern.indexOf('?') >= 0 || aPattern.indexOf('[') >= 0;
    }

    private static Path literalPrefixOf(String aPattern) {
        StringBuilder prefix = new StringBuilder("/");
        for (String segment : aPattern.substring(1).split("/")) {
            if (hasWildcard(segment)) {
                break;
            }
            if (prefix.length() > 1) {
                prefix.append('/');
            }
            prefix.append(segment);
        }
        return Paths.get(prefix.toString());
    }
}
