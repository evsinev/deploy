package io.pne.deploy.agent.steps;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names of the variables a plan produces while it runs, in declaration order.
 *
 * <p>Only a step that resolves a value at run time (such as reading the wanted version from a URL) adds a name here;
 * everything else is already resolved by the sender. Validation uses the scope to reject a reference to a variable
 * that no earlier step declares.
 */
public class StepPlanScope {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final Set<String> declared = new LinkedHashSet<>();

    public void declare(String aName) {
        declared.add(aName);
    }

    public boolean isDeclared(String aName) {
        return declared.contains(aName);
    }

    public Set<String> getDeclared() {
        return declared;
    }

    /** Fails when {@code aValue} references a variable that no earlier step declared. */
    public void checkReferences(String aStepType, String aParamName, String aValue) throws StepValidationException {
        if (aValue == null) {
            return;
        }
        Matcher matcher = VARIABLE.matcher(aValue);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!declared.contains(name)) {
                throw new StepValidationException("step '" + aStepType + "', parameter '" + aParamName
                        + "': unknown variable ${" + name + "}"
                        + (declared.isEmpty() ? ", no variables are declared by earlier steps"
                                              : ", variables declared by earlier steps are " + declared));
            }
        }
    }

    /** Replaces {@code ${name}} with a value that stands in for the real one, for checks made before running. */
    public static String withPlaceholders(String aValue, String aPlaceholder) {
        if (aValue == null) {
            return null;
        }
        return VARIABLE.matcher(aValue).replaceAll(Matcher.quoteReplacement(aPlaceholder));
    }

    public static Pattern variablePattern() {
        return VARIABLE;
    }
}
