package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.commands.VersionFetcher;
import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.StepPolicy;
import io.pne.deploy.agent.steps.policy.UrlGuard;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads a version from a URL and makes it available to later steps as a variable.
 *
 * <p>This is the only value a plan discovers while it runs, and it ends up in file names and paths, so it has to
 * match a strict pattern: a version and nothing that could be read as a path.
 */
public class ResolveVersionStep implements IStep {

    public static final String TYPE = "resolve-version";

    public static final String DEFAULT_PATTERN = "^[0-9][0-9A-Za-z.\\-]*$";

    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final String  url;
    private final String  variable;
    private final Pattern pattern;
    private final int     timeoutSeconds;

    public ResolveVersionStep(StepParams aParams) throws StepValidationException {
        url            = aParams.required("url");
        variable       = aParams.required("var");
        timeoutSeconds = aParams.intValue("timeoutSeconds", 30);

        String patternText = aParams.optional("pattern", DEFAULT_PATTERN);
        try {
            pattern = Pattern.compile(patternText);
        } catch (PatternSyntaxException e) {
            throw aParams.error("pattern", "is not a valid regular expression: " + e.getMessage());
        }
        if (!VARIABLE_NAME.matcher(variable).matches()) {
            throw aParams.error("var", "must be a plain name, got '" + variable + "'");
        }
        if (timeoutSeconds <= 0) {
            throw aParams.error("timeoutSeconds", "must be positive, got " + timeoutSeconds);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "url", url);
        UrlGuard.check(aPolicy.getStatusHosts(), TYPE, "url", StepPlanScope.withPlaceholders(url, "0"));

        if (timeoutSeconds > aPolicy.getMaxStepSeconds()) {
            throw new StepValidationException("step '" + TYPE + "': timeoutSeconds " + timeoutSeconds
                    + " is over the limit of " + aPolicy.getMaxStepSeconds() + "s");
        }
        if (aScope.isDeclared(variable)) {
            throw new StepValidationException("step '" + TYPE + "': variable ${" + variable + "} is already declared");
        }
        aScope.declare(variable);
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        try {
            String expandedUrl = aContext.expand(url);
            UrlGuard.check(aContext.getPolicy().getStatusHosts(), TYPE, "url", expandedUrl);

            String value = VersionFetcher.fetch(expandedUrl, timeoutSeconds).trim();
            if (!pattern.matcher(value).matches()) {
                throw new StepExecutionException(expandedUrl + " answered '" + value
                        + "', which does not match " + pattern.pattern());
            }
            aContext.setVariable(variable, value);
            aContext.log(expandedUrl + " resolved ${" + variable + "} to " + value);

        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot read a version from " + url + ": " + e, e);
        }
    }

    @Override
    public long getMaxSeconds() {
        return timeoutSeconds;
    }
}
