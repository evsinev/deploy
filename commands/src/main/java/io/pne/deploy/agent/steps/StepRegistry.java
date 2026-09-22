package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.impl.CheckVersionStep;
import io.pne.deploy.agent.steps.impl.CopyFileStep;
import io.pne.deploy.agent.steps.impl.FetchStep;
import io.pne.deploy.agent.steps.impl.ResolveVersionStep;
import io.pne.deploy.agent.steps.impl.SignalServiceStep;
import io.pne.deploy.agent.steps.impl.SleepStep;
import io.pne.deploy.agent.steps.impl.UnpackStep;
import io.pne.deploy.agent.steps.impl.WaitUrlStep;
import io.pne.deploy.agent.steps.impl.WriteFileStep;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The step types this build knows.
 *
 * <p>The same registry is used on both sides: the sender uses it to reject a plan that names a type the receiver
 * could not run, the receiver uses it to build the steps it is asked to run.
 */
public class StepRegistry {

    private final Map<String, IStepFactory> factories;

    public StepRegistry(Map<String, IStepFactory> aFactories) {
        factories = new LinkedHashMap<>(aFactories);
    }

    public static StepRegistry defaults() {
        Map<String, IStepFactory> factories = new LinkedHashMap<>();
        factories.put(FetchStep.TYPE,           FetchStep::new);
        factories.put(UnpackStep.TYPE,          UnpackStep::new);
        factories.put(WriteFileStep.TYPE,       WriteFileStep::new);
        factories.put(CopyFileStep.TYPE,        CopyFileStep::new);
        factories.put(SignalServiceStep.TYPE,   SignalServiceStep::new);
        factories.put(SleepStep.TYPE,           SleepStep::new);
        factories.put(CheckVersionStep.TYPE,    CheckVersionStep::new);
        factories.put(WaitUrlStep.TYPE,         WaitUrlStep::new);
        factories.put(ResolveVersionStep.TYPE,  ResolveVersionStep::new);
        return new StepRegistry(factories);
    }

    public Set<String> getTypes() {
        return Collections.unmodifiableSet(new TreeSet<>(factories.keySet()));
    }

    public boolean hasType(String aType) {
        return factories.containsKey(aType);
    }

    public IStep create(AgentStep aStep) throws StepValidationException {
        String type = aStep == null ? "" : aStep.getType();
        if (type.isEmpty()) {
            throw new StepValidationException("step has no type; known types are " + getTypes());
        }
        IStepFactory factory = factories.get(type);
        if (factory == null) {
            throw new StepValidationException("unknown step type '" + type + "'; known types are " + getTypes());
        }
        StepParams params = new StepParams(type, aStep.getParams());
        IStep      step   = factory.create(params);
        params.checkNoUnknownParams();
        return step;
    }
}
