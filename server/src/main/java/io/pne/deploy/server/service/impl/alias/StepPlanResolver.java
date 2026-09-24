package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.agent.steps.impl.ResolveVersionStep;
import io.pne.deploy.server.service.impl.alias.recipe.RecipeDescription;
import io.pne.deploy.server.service.impl.alias.recipe.RecipeLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns one command of an alias into the steps an agent will run.
 *
 * <p>Everything an alias knows is filled in here, on the server, so the whole plan can be shown before it is sent
 * and refused before it reaches a host. The one thing left open is a value a step works out while it runs; that
 * reference travels as it stands and the agent fills it in.
 */
public class StepPlanResolver {

    private final RecipeLoader recipes;
    private final SiteVars     site;
    private final StepRegistry registry;

    public StepPlanResolver(RecipeLoader aRecipes, SiteVars aSite, StepRegistry aRegistry) {
        recipes  = aRecipes;
        site     = aSite;
        registry = aRegistry;
    }

    public List<AgentStep> resolve(String aWhere, AliasCommand aCommand, Map<String, String> aAliasValues) throws IOException {
        if (aCommand.recipe != null) {
            return fromRecipe(aWhere, aCommand, aAliasValues);
        }
        return fromSteps(aWhere, aCommand.steps, withSite(aAliasValues));
    }

    private List<AgentStep> fromRecipe(String aWhere, AliasCommand aCommand, Map<String, String> aAliasValues) throws IOException {
        RecipeDescription recipe = recipes.load(aCommand.recipe);
        String            where  = aWhere + ", recipe '" + aCommand.recipe + "'";

        Map<String, String> given = new LinkedHashMap<>();
        if (aCommand.with != null) {
            VariableResolver outer = new VariableResolver(withSite(aAliasValues), deferredNames(recipe.steps));
            aCommand.with.forEach((name, value) ->
                    given.put(name, outer.resolve(aWhere + ", with." + name, text(aWhere + ", with." + name, value))));
        }

        List<AliasParam> declared = recipe.getParams();
        checkNothingUnexpected(where, given, declared);

        Map<String, String> values = new LinkedHashMap<>();
        site.getValues().forEach((name, value) -> values.put(SiteVars.SCOPE + "." + name, value));

        Set<String> deferred = deferredNames(recipe.steps);
        for (AliasParam param : declared) {
            String value = given.get(param.name);
            if (value == null || value.isEmpty()) {
                // A fresh resolver each time: a default may use the values declared above it.
                value = param.defaultValue == null
                        ? null
                        : new VariableResolver(values, deferred)
                                .resolve(where + ", default of '" + param.name + "'", param.defaultValue);
            }
            if ((value == null || value.isEmpty()) && param.required) {
                throw new IllegalArgumentException(where + " needs a value for '" + param.name + "'"
                        + (param.description == null ? "" : " (" + param.description + ")"));
            }
            if (value != null) {
                // Checked here as well as where it came from: a value that reaches a recipe through another
                // value, or through a default, would otherwise never be held to what the recipe declared.
                values.put(param.name, checkUnlessDeferred(where, param, value, deferred));
            }
        }

        return fromSteps(where, recipe.steps, values);
    }

    private List<AgentStep> fromSteps(String aWhere, List<AliasStep> aSteps, Map<String, String> aValues) {
        if (aSteps == null || aSteps.isEmpty()) {
            throw new IllegalArgumentException(aWhere + " has no steps");
        }

        VariableResolver resolver = new VariableResolver(aValues, deferredNames(aSteps));
        List<AgentStep>  plan     = new ArrayList<>();

        for (int i = 0; i < aSteps.size(); i++) {
            AliasStep step  = aSteps.get(i);
            String    where = aWhere + ", step " + (i + 1);

            if (step == null) {
                throw new IllegalArgumentException(where + " is empty");
            }
            if (step.type == null || step.type.trim().isEmpty()) {
                throw new IllegalArgumentException(where + " has no type; known types are " + registry.getTypes());
            }
            if (!registry.hasType(step.type)) {
                throw new IllegalArgumentException(where + ": unknown step type '" + step.type
                        + "'; known types are " + registry.getTypes());
            }

            Map<String, String> params = new LinkedHashMap<>();
            if (step.params != null) {
                step.params.forEach((name, value) -> {
                    String at = where + " '" + step.type + "', parameter '" + name + "'";
                    params.put(name, resolver.resolve(at, text(at, value)));
                });
            }
            plan.add(new AgentStep(step.type, params));
        }
        return plan;
    }

    /**
     * Checks a value against what the recipe declared, unless it still holds a reference the agent fills in
     * while the plan runs - there is nothing to check yet in that case.
     */
    private static String checkUnlessDeferred(String aWhere, AliasParam aParam, String aValue, Set<String> aDeferred) {
        for (String name : aDeferred) {
            if (aValue.contains("${" + name + "}")) {
                return aValue;
            }
        }
        return ParamChecker.check(aWhere, aParam, aValue);
    }

    /** Names that only exist once the plan is running, because a step works them out then. */
    private static Set<String> deferredNames(List<AliasStep> aSteps) {
        Set<String> names = new LinkedHashSet<>();
        if (aSteps != null) {
            for (AliasStep step : aSteps) {
                if (step != null && ResolveVersionStep.TYPE.equals(step.type) && step.params != null) {
                    Object name = step.params.get("var");
                    if (name != null) {
                        names.add(String.valueOf(name));
                    }
                }
            }
        }
        return names;
    }

    private Map<String, String> withSite(Map<String, String> aValues) {
        Map<String, String> values = new LinkedHashMap<>(aValues);
        site.getValues().forEach((name, value) -> values.put(SiteVars.SCOPE + "." + name, value));
        return values;
    }

    private static void checkNothingUnexpected(String aWhere, Map<String, String> aGiven, List<AliasParam> aDeclared) {
        Set<String> declared = new TreeSet<>();
        aDeclared.forEach(param -> declared.add(param.name));

        Set<String> unexpected = new TreeSet<>(aGiven.keySet());
        unexpected.removeAll(declared);
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException(aWhere + " was given " + unexpected
                    + ", which it does not expect; it expects " + declared);
        }
    }

    /** Values written as numbers or flags in the file arrive here as such and travel on as text. */
    private static String text(String aWhere, Object aValue) {
        if (aValue == null) {
            throw new IllegalArgumentException(aWhere + " has no value");
        }
        if (aValue instanceof Map || aValue instanceof List) {
            throw new IllegalArgumentException(aWhere + " must be a single value, not " + aValue.getClass().getSimpleName());
        }
        return String.valueOf(aValue);
    }
}
