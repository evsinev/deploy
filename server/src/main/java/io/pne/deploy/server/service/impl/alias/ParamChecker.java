package io.pne.deploy.server.service.impl.alias;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks one value against what was declared for it.
 *
 * <p>The same check is applied wherever a value enters: a word of a task line, a value handed to a recipe, or a
 * default written in a recipe. A value that only gets checked in one of those places is a value that can be got
 * in through the others.
 */
public class ParamChecker {

    private ParamChecker() {
    }

    public static String check(String aWhere, AliasParam aParam, String aValue) {
        if (aParam == null || aParam.name == null || aParam.name.trim().isEmpty()) {
            throw new IllegalArgumentException(aWhere + ": a declared value has no name");
        }
        if (aValue == null) {
            return null;
        }

        ParamType type = type(aWhere, aParam);

        if (type == ParamType.ENUM) {
            if (aParam.values == null || !aParam.values.contains(aValue)) {
                throw new IllegalArgumentException(aWhere + ": '" + aParam.name + "' must be one of "
                        + aParam.values + ", got '" + aValue + "'");
            }
            return aValue;
        }

        String pattern = aParam.pattern != null ? aParam.pattern : type.getPattern();
        if (pattern == null) {
            throw new IllegalArgumentException(aWhere + ": '" + aParam.name + "' is a string and needs a pattern");
        }
        if (!matches(aWhere, aParam, pattern, aValue)) {
            throw new IllegalArgumentException(aWhere + ": '" + aParam.name + "' is '" + aValue
                    + "', which is not a valid " + type.name().toLowerCase(Locale.ROOT)
                    + " (expected " + pattern + ")");
        }
        return aValue;
    }

    public static ParamType type(String aWhere, AliasParam aParam) {
        try {
            return ParamType.of(aParam.type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(aWhere + ", '" + aParam.name + "': " + e.getMessage(), e);
        }
    }

    private static boolean matches(String aWhere, AliasParam aParam, String aPattern, String aValue) {
        try {
            return Pattern.compile(aPattern).matcher(aValue).matches();
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(aWhere + ": the pattern of '" + aParam.name
                    + "' is not a valid regular expression: " + e.getMessage(), e);
        }
    }
}
