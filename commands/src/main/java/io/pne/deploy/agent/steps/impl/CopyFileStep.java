package io.pne.deploy.agent.steps.impl;

import io.pne.deploy.agent.steps.IStep;
import io.pne.deploy.agent.steps.StepContext;
import io.pne.deploy.agent.steps.StepExecutionException;
import io.pne.deploy.agent.steps.StepParams;
import io.pne.deploy.agent.steps.StepPlanScope;
import io.pne.deploy.agent.steps.StepValidationException;
import io.pne.deploy.agent.steps.policy.PathGuard;
import io.pne.deploy.agent.steps.policy.StepPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Copies the contents of one file over another, leaving the permissions of an existing destination untouched.
 *
 * <p>Two modes: {@code replace} writes the copy beside the destination and moves it into place, so a reader sees
 * either the old contents or the new ones; {@code in-place} empties the destination and writes into it, as
 * {@code cat source > target} does, so it stays the same file - for a file mounted on its own into a container,
 * which a replacement would never reach.
 */
public class CopyFileStep implements IStep {

    public static final String TYPE = "copy-file";

    public static final String MODE_REPLACE  = "replace";
    public static final String MODE_IN_PLACE = "in-place";

    private static final List<String> MODES = Arrays.asList(MODE_REPLACE, MODE_IN_PLACE);

    private final String from;
    private final String to;
    private final String mode;

    public CopyFileStep(StepParams aParams) throws StepValidationException {
        from = aParams.required("from");
        to   = aParams.required("to");
        mode = aParams.optional("mode", MODE_REPLACE);
        if (!MODES.contains(mode)) {
            throw aParams.error("mode", "must be one of " + MODES + ", got '" + mode + "'");
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "from", from);
        aScope.checkReferences(TYPE, "to", to);
        PathGuard.checkReadable(aPolicy, TYPE, "from", StepPlanScope.withPlaceholders(from, "0"));
        PathGuard.checkWritable(aPolicy, TYPE, "to", StepPlanScope.withPlaceholders(to, "0"));
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        try {
            Path source = PathGuard.checkReadable(aContext.getPolicy(), TYPE, "from", aContext.expand(from));
            Path target = PathGuard.checkWritable(aContext.getPolicy(), TYPE, "to", aContext.expand(to));

            if (!Files.isRegularFile(source)) {
                throw new StepExecutionException("Not a file: " + source);
            }
            if (MODE_IN_PLACE.equals(mode)) {
                FileOperations.copyInPlace(source, target);
            } else {
                FileOperations.copy(source, target);
            }
            aContext.log("copied " + Files.size(target) + " byte(s) from " + source + " to " + target + " (" + mode + ")");
        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot copy " + from + " to " + to + ": " + e, e);
        }
    }
}
