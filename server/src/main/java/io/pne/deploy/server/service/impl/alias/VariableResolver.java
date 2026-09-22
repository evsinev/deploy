package io.pne.deploy.server.service.impl.alias;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the declared values into the places that refer to them.
 *
 * <p>The replacement happens on the values of an already parsed file, never on its text. A version can therefore
 * only ever become a version: it cannot add a step, change an agent or alter the shape of the file, whatever it
 * contains.
 */
public class VariableResolver {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?)}");

    private final Map<String, String> values;
    private final Set<String>         deferred;

    /**
     * @param aValues   what is known now
     * @param aDeferred names that a step works out while it runs; these are left untouched for the agent
     */
    public VariableResolver(Map<String, String> aValues, Set<String> aDeferred) {
        values   = new LinkedHashMap<>(aValues);
        deferred = new TreeSet<>(aDeferred);
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String resolve(String aWhere, String aValue) {
        if (aValue == null || aValue.indexOf("${") < 0) {
            return aValue;
        }

        Matcher       matcher = REFERENCE.matcher(aValue);
        StringBuilder result  = new StringBuilder();

        while (matcher.find()) {
            String name        = matcher.group(1);
            String replacement = values.get(name);

            if (replacement == null) {
                if (deferred.contains(name)) {
                    replacement = matcher.group();          // the agent fills this in while the plan runs
                } else {
                    throw new IllegalArgumentException(aWhere + ": unknown variable ${" + name + "}"
                            + ". Known variables are " + new TreeSet<>(values.keySet())
                            + (deferred.isEmpty() ? "" : " and, while the plan runs, " + deferred));
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Fails when text still holds the older numbered placeholders, which are not substituted in this form. */
    public static void checkNoNumberedPlaceholders(String aWhere, String aText) {
        if (aText == null) {
            return;
        }
        Matcher matcher = Pattern.compile("\\$(\\d+|ISSUE_ID)").matcher(aText);
        if (matcher.find()) {
            throw new IllegalArgumentException(aWhere + ": '$" + matcher.group(1)
                    + "' is the older form of a value and is not filled in here."
                    + " Declare the value under 'params:' and write ${name} instead.");
        }
    }
}
