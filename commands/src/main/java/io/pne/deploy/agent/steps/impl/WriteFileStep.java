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
import java.nio.file.Path;

/**
 * Writes a short text file, typically the version marker a service reads when it starts.
 *
 * <p>A trailing newline is written by default, matching what a shell redirect produces, because the readers of these
 * markers expect it. The write is atomic so a service restarting at the same moment never sees a half-written file.
 */
public class WriteFileStep implements IStep {

    public static final String TYPE = "write-file";

    private final String  path;
    private final String  content;
    private final boolean newline;
    private final boolean mkdirs;
    private final boolean atomic;

    public WriteFileStep(StepParams aParams) throws StepValidationException {
        path    = aParams.required("path");
        content = aParams.optional("content", "");
        newline = aParams.boolValue("newline", true);
        mkdirs  = aParams.boolValue("mkdirs", false);
        atomic  = aParams.boolValue("atomic", true);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void validate(StepPolicy aPolicy, StepPlanScope aScope) throws StepValidationException {
        aScope.checkReferences(TYPE, "path", path);
        aScope.checkReferences(TYPE, "content", content);
        PathGuard.checkWritable(aPolicy, TYPE, "path", StepPlanScope.withPlaceholders(path, "0"));
    }

    @Override
    public void execute(StepContext aContext) throws StepExecutionException {
        String text = aContext.expand(content) + (newline ? System.lineSeparator() : "");
        try {
            Path target = PathGuard.checkWritable(aContext.getPolicy(), TYPE, "path", aContext.expand(path));
            if (mkdirs) {
                FileOperations.createDirectories(target.getParent());
            }
            FileOperations.writeString(target, text, atomic);
            aContext.log("wrote " + text.length() + " byte(s) to " + target);
        } catch (StepValidationException e) {
            throw new StepExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new StepExecutionException("Cannot write " + path + ": " + e, e);
        }
    }
}
