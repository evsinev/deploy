package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.StepRegistry;
import io.pne.deploy.server.api.task.*;
import io.pne.deploy.server.service.impl.InputTextChecker;
import io.pne.deploy.server.service.impl.alias.recipe.RecipeLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.pne.deploy.server.api.task.AgentFinder.agentByName;

/**
 * Builds the task a line of text asks for.
 *
 * <p>An alias that declares its values is turned into a plan of typed steps, resolved here so that the whole of
 * it can be shown before anything is sent. An alias in the older form still becomes a command that starts a
 * program on the agent, which is what lets the two live side by side while files are moved over one at a time.
 */
public class AliasParser {

    private final AliasDescriptionLoader loader;
    private final RecipeLoader           recipes;
    private final StepRegistry           registry;
    private final InputTextChecker       checker = new InputTextChecker();

    public AliasParser(File aAliasesDir) {
        this(aAliasesDir, new File(aAliasesDir.getParentFile(), "recipes"));
    }

    public AliasParser(File aAliasesDir, File aRecipesDir) {
        if(!aAliasesDir.exists()) {
            throw new IllegalStateException("Aliases dir " + aAliasesDir + " not exists");
        }
        loader   = new AliasDescriptionLoader(aAliasesDir);
        recipes  = new RecipeLoader(aRecipesDir);
        registry = StepRegistry.defaults();
    }

    public Task parseAlias(String aText, int aIssueId) throws IOException {
        checker.checkAlias(aText);
        AliasParameters aliasParameters = new AliasParameters(aText);
        AliasDescription description    = loader.loadAlias(aliasParameters, aIssueId);
        return convertAliasDescriptionToTask(description, aliasParameters, aText, aIssueId);
    }

    public AliasDescriptionLoader getLoader() {
        return loader;
    }

    public RecipeLoader getRecipes() {
        return recipes;
    }

    private Task convertAliasDescriptionToTask(
              AliasDescription aDescription
            , AliasParameters  aParameters
            , String           aText
            , int              aIssueId
    ) throws IOException {

        if(aDescription.commands == null) {
            throw new IllegalArgumentException("description.commands is null");
        }

        boolean              declared = aDescription.params != null;
        TaskLineParser.Parsed parsed  = declared
                ? TaskLineParser.parse(aParameters.name, aParameters.parameters, aDescription.params)
                : null;

        Map<String, String> values = new LinkedHashMap<>();
        if (declared) {
            parsed.getValues().forEach((name, value) -> {
                if (value != null) {
                    values.put(name, value);
                }
            });
            values.put("issueId", String.valueOf(aIssueId));
            values.put("alias", aParameters.name);
        }

        StepPlanResolver  resolver = declared
                ? new StepPlanResolver(recipes, SiteVars.load(loader.getAliasDir()), registry)
                : null;

        List<TaskCommand> commands = new ArrayList<>();
        for (int i = 0; i < aDescription.commands.size(); i++) {
            AliasCommand command = aDescription.commands.get(i);
            String       where   = "Alias " + aParameters.name + ", command " + (i + 1);

            checkOneForm(where, command, declared);

            AgentCommand agentCommand;
            if (command.recipe != null || command.steps != null) {
                List<AgentStep> steps = resolver.resolve(where, command, values);
                agentCommand = AgentCommand.ofSteps(planName(command, aParameters.name), steps);
            } else {
                agentCommand = new AgentCommand(
                          new AgentCommandParameters()
                        , AgentCommandType.SHELL
                        , command.name
                        , resolveArguments(where, command, values, declared)
                );
            }
            commands.add(new TaskCommand(agentByName(command.agents), agentCommand));
        }

        TaskDiff diff = buildDiff(aDescription, parsed);
        return new Task(TaskId.generateTaskId(), new TaskParameters(), commands, aText, aIssueId, diff);
    }

    private static void checkOneForm(String aWhere, AliasCommand aCommand, boolean aDeclared) {
        int forms = 0;
        if (aCommand.name   != null) forms++;
        if (aCommand.recipe != null) forms++;
        if (aCommand.steps  != null) forms++;

        if (forms != 1) {
            throw new IllegalArgumentException(aWhere + " must say exactly one of 'name', 'recipe' or 'steps'");
        }
        if (!aDeclared && (aCommand.recipe != null || aCommand.steps != null)) {
            throw new IllegalArgumentException(aWhere + " uses steps, which need the alias to declare its values"
                    + " under 'params:'");
        }
        if (aCommand.agents == null || aCommand.agents.trim().isEmpty()) {
            throw new IllegalArgumentException(aWhere + " does not say which agents to run on");
        }
    }

    private List<String> resolveArguments(String aWhere, AliasCommand aCommand, Map<String, String> aValues, boolean aDeclared) {
        if (aCommand.arguments == null) {
            return Collections.emptyList();
        }
        if (!aDeclared) {
            return aCommand.arguments;
        }
        VariableResolver resolver  = new VariableResolver(aValues, Collections.emptySet());
        List<String>     arguments = new ArrayList<>();
        for (int i = 0; i < aCommand.arguments.size(); i++) {
            String argument = aCommand.arguments.get(i);
            String at       = aWhere + ", argument " + (i + 1);
            VariableResolver.checkNoNumberedPlaceholders(at, argument);
            arguments.add(resolver.resolve(at, argument));
        }
        return arguments;
    }

    private static String planName(AliasCommand aCommand, String aAliasName) {
        return aCommand.recipe != null ? aCommand.recipe : aAliasName;
    }

    private static TaskDiff buildDiff(AliasDescription aDescription, TaskLineParser.Parsed aParsed) {
        if (aDescription.diff == null) {
            return null;
        }

        int newVersionArg = aDescription.diff.newVersionArg;
        if (aDescription.diff.versionParam != null) {
            if (aParsed == null) {
                throw new IllegalArgumentException("diff.versionParam needs the alias to declare its values"
                        + " under 'params:'");
            }
            if (newVersionArg != 0) {
                throw new IllegalArgumentException("diff says both 'newVersionArg' and 'versionParam';"
                        + " keep only 'versionParam'");
            }
            newVersionArg = aParsed.getLineIndex(aDescription.diff.versionParam);
            if (newVersionArg == 0) {
                throw new IllegalArgumentException("diff.versionParam names '" + aDescription.diff.versionParam
                        + "', which is not on the task line");
            }
        }

        return TaskDiff.builder()
                .enabled(aDescription.diff.enabled)
                .versionUrl(aDescription.diff.versionUrl)
                .gitlabProjectId(aDescription.diff.gitlabProjectId)
                .agent(aDescription.diff.agent)
                .newVersionArg(newVersionArg)
                .project(aDescription.diff.project)
                .app(aDescription.diff.app)
                .instance(aDescription.diff.instance)
                .build();
    }
}
