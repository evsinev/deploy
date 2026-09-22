package io.pne.deploy.agent.steps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Typed access to the flat string parameters of one step, with error messages that name the step and the parameter.
 *
 * <p>Every read marks the parameter as known; {@link #checkNoUnknownParams()} then rejects anything left over, so a
 * misspelled parameter fails loudly instead of being silently ignored.
 */
public class StepParams {

    private final String              type;
    private final Map<String, String> values;
    private final Set<String>         known = new LinkedHashSet<>();

    public StepParams(String aType, Map<String, String> aValues) {
        type   = aType;
        values = aValues == null ? Collections.emptyMap() : new LinkedHashMap<>(aValues);
    }

    public String getType() {
        return type;
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public String required(String aName) throws StepValidationException {
        String value = optional(aName, null);
        if (value == null || value.isEmpty()) {
            throw error(aName, "is required");
        }
        return value;
    }

    public String optional(String aName, String aDefault) {
        known.add(aName);
        String value = values.get(aName);
        return value == null ? aDefault : value.trim();
    }

    public int intValue(String aName, int aDefault) throws StepValidationException {
        String value = optional(aName, null);
        if (value == null || value.isEmpty()) {
            return aDefault;
        }
        rejectUnresolved(aName, value);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw error(aName, "must be a whole number, got '" + value + "'");
        }
    }

    public int requiredInt(String aName) throws StepValidationException {
        String value = required(aName);
        rejectUnresolved(aName, value);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw error(aName, "must be a whole number, got '" + value + "'");
        }
    }

    public boolean boolValue(String aName, boolean aDefault) throws StepValidationException {
        String value = optional(aName, null);
        if (value == null || value.isEmpty()) {
            return aDefault;
        }
        rejectUnresolved(aName, value);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw error(aName, "must be true or false, got '" + value + "'");
    }

    /** Rejects parameters that were never read, which is almost always a typo in the plan. */
    public void checkNoUnknownParams() throws StepValidationException {
        Set<String> unknown = new TreeSet<>(values.keySet());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new StepValidationException("step '" + type + "': unknown parameter(s) " + unknown
                    + ", known parameters are " + new TreeSet<>(known));
        }
    }

    public StepValidationException error(String aName, String aProblem) {
        return new StepValidationException("step '" + type + "', parameter '" + aName + "' " + aProblem);
    }

    private void rejectUnresolved(String aName, String aValue) throws StepValidationException {
        if (aValue.contains("${")) {
            throw error(aName, "cannot use a variable here, got '" + aValue + "'");
        }
    }
}
