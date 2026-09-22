package io.pne.deploy.agent.steps;

/** A step or its parameters are rejected before anything is executed. */
public class StepValidationException extends Exception {

    public StepValidationException(String aMessage) {
        super(aMessage);
    }

    public StepValidationException(String aMessage, Throwable aCause) {
        super(aMessage, aCause);
    }
}
