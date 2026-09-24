package io.pne.deploy.server.service.impl.alias;

import java.util.List;
import java.util.Map;

/**
 * One command of an alias: what to do, and on which agents.
 *
 * <p>Exactly one of three forms is used. {@code recipe} names a shared list of steps and passes it values,
 * {@code steps} writes the steps out in place for a one-off, and {@code name} is the older form that starts a
 * program on the agent and stays until everything has moved over.
 */
public class AliasCommand {

    public String agents;

    /** Older form: the program to run on the agent. */
    public String name;

    /** Older form: the arguments for {@link #name}. */
    public List<String> arguments;

    /** Name of a file in the recipes directory, without the extension. */
    public String recipe;

    /** Values for the recipe. */
    public Map<String, Object> with;

    /** Steps written out in place, instead of naming a recipe. */
    public List<AliasStep> steps;

    @Override
    public String toString() {
        return "AliasCommand{agents='" + agents + "', "
                + (recipe != null ? "recipe='" + recipe + "'" : steps != null ? "steps=" + steps : "name='" + name + "'")
                + '}';
    }
}
