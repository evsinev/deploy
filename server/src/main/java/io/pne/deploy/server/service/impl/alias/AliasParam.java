package io.pne.deploy.server.service.impl.alias;

import java.util.List;

/**
 * One value an alias or a recipe expects.
 *
 * <p>Declaring the values makes a task line checkable before anything runs: a missing value is reported with a
 * usage line instead of reaching an agent as an empty string, and a value that is not shaped like a version
 * cannot travel on into a file name.
 */
public class AliasParam {

    /** Name used as {@code ${name}} in the rest of the file. */
    public String name;

    /** One of the names in {@link ParamType}; defaults to a version. */
    public String type;

    public boolean required = true;

    /** Used when the value is not given; only meaningful when {@code required} is false. */
    public String defaultValue;

    /** Overrides the pattern of the type, for a {@code string} value with its own shape. */
    public String pattern;

    /** Allowed values, for an {@code enum}. */
    public List<String> values;

    /** Which word of the task line holds the value, counting from 1. */
    public Integer position;

    /** A keyword in the task line; the value is the word after it. Use instead of {@code position}. */
    public String key;

    public String description;

    @Override
    public String toString() {
        return name + (required ? "" : "?");
    }
}
