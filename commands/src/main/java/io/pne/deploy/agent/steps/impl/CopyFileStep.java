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

/** Copies the contents of one file over another, leaving the permissions of an existing destination untouched. */
public class CopyFileStep implements IStep {

    public static final String TYPE = "copy-file";

    private final String  from;
    private final String  to;
    private final boolean atomic;

    public CopyFileStep(StepParams aParams) throws StepValidationException {
        from   = aParams.required("from");
        to     = aParams.required("to");
        atomic = aParams.boolValue("atomic", true);
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
            FileOperations.copy(source, target, atomic);
            aContext.log("copied " + Files.size(target) + " byte(s) from " + source + " to " + target);
        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot copy " + from + " to " + to + ": " + e, e);
        }
    }
}
