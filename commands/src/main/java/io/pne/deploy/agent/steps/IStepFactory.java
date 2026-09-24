package io.pne.deploy.agent.steps;

/** Builds a step of one type from its wire parameters. */
public interface IStepFactory {

    IStep create(StepParams aParams) throws StepValidationException;
}
