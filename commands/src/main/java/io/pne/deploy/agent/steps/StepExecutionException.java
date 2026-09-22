package io.pne.deploy.agent.steps;

/** A step failed while running. The plan stops; earlier steps are not undone. */
public class StepExecutionException extends Exception {

    public StepExecutionException(String aMessage) {
        super(aMessage);
    }

    public StepExecutionException(String aMessage, Throwable aCause) {
        super(aMessage, aCause);
    }
}
