package io.pne.deploy.server.service.impl.alias;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The shapes a declared value may take.
 *
 * <p>The patterns are deliberately narrow. A version, a name or a path that has to match one of these cannot
 * carry a path separator or a {@code ..} into the step that uses it, which is what keeps a value coming from a
 * task line from reaching outside where it belongs.
 */
public enum ParamType {

    VERSION("^[0-9][0-9A-Za-z.\\-]*$"),
    INT    ("^[0-9]{1,9}$"),
    NAME   ("^[A-Za-z0-9][A-Za-z0-9._\\-]*$"),
    URL    ("^https?://[A-Za-z0-9.\\-]+(:[0-9]+)?(/[^\\s]*)?$"),
    PATH   ("^/[^\\s]*$"),
    ENUM   (null),
    STRING (null);

    private final String pattern;

    ParamType(String aPattern) {
        pattern = aPattern;
    }

    public String getPattern() {
        return pattern;
    }

    public static ParamType of(String aName) {
        if (aName == null || aName.trim().isEmpty()) {
            return VERSION;
        }
        for (ParamType type : values()) {
            if (type.name().equalsIgnoreCase(aName.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown parameter type '" + aName + "'; known types are " + names());
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(type -> type.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList());
    }
}
