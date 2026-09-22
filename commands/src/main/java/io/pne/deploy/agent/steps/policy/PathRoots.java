package io.pne.deploy.agent.steps.policy;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
 *
 * <p>The fixed leading part of every entry is resolved through symbolic links when the roots are built, because
 * the paths being checked are resolved the same way. On a system where a directory such as the configuration
 * tree is itself a link, comparing a resolved path against an unresolved root would refuse everything under it.
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

            Path   literal = literalPrefixOf(trimmed);
            Path   real    = resolve(literal);
            String tail    = trimmed.substring(literal.toString().length());

            patterns.add(real + tail);
            literalPrefixes.add(real);
            // The resolved part is a filename, not a pattern: escape it before it joins one, or a directory
            // whose name happens to contain a glob character would quietly widen what the root allows.
            matchers.add(hasWildcard(trimmed)
                    ? FileSystems.getDefault().getPathMatcher("glob:" + escapeGlob(real.toString()) + tail)
                    : null);
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

    /** Where the fixed leading part of a root really is, or the part itself when it does not exist yet. */
    private static Path resolve(Path aPrefix) {
        if (!Files.exists(aPrefix)) {
            return aPrefix;
        }
        try {
            return aPrefix.toRealPath();
        } catch (IOException e) {
            return aPrefix;
        }
    }

    /** Makes a literal path safe to put inside a glob. */
    private static String escapeGlob(String aPath) {
        StringBuilder escaped = new StringBuilder(aPath.length());
        for (char c : aPath.toCharArray()) {
            if (c == '\\' || c == '*' || c == '?' || c == '[' || c == ']' || c == '{' || c == '}') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
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
