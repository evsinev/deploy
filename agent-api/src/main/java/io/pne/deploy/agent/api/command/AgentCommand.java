package io.pne.deploy.agent.api.command;

import io.pne.deploy.agent.api.messages.IAgentServerMessage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentCommand implements IAgentServerMessage {

     /**
      * Name shown for a {@link AgentCommandType#STEPS} command. It is not a program name, and it is shaped so that
      * an agent too old to know about STEPS treats it as a path that does not exist and fails at once, rather than
      * looking it up among the programs it can run.
      */
     public static final String STEPS_NAME_PREFIX = "./steps-plan/";

     @Nonnull public final AgentCommandParameters parameters;
     @Nonnull public final AgentCommandType       type;
     @Nonnull public final String                 name;
     @Nonnull public final List<String>           arguments;

     /** Set for {@link AgentCommandType#STEPS} only; {@code null} for legacy shell commands. */
     @Nullable public final List<AgentStep>       steps;

    public AgentCommand(@Nonnull AgentCommandParameters parameters, @Nonnull AgentCommandType type, @Nonnull String name, @Nonnull List<String> arguments) {
        this(parameters, type, name, arguments, null);
    }

    public AgentCommand(@Nonnull AgentCommandParameters parameters, @Nonnull AgentCommandType type, @Nonnull String name, @Nonnull List<String> arguments, @Nullable List<AgentStep> steps) {
        this.parameters = parameters;
        this.type = type;
        this.name = name;
        this.arguments = arguments;
        this.steps = steps;
    }

    /** A step plan command. {@code aDisplayName} names what the plan does and is used in logs and the dashboard. */
    public static AgentCommand ofSteps(@Nonnull String aDisplayName, @Nonnull List<AgentStep> aSteps) {
        return new AgentCommand(
                  new AgentCommandParameters()
                , AgentCommandType.STEPS
                , STEPS_NAME_PREFIX + aDisplayName
                , Collections.emptyList()
                , new ArrayList<>(aSteps)
        );
    }

    @Nonnull
    public List<AgentStep> getSteps() {
        return steps == null ? Collections.emptyList() : steps;
    }

    @Override
    public String toString() {
        return "AgentCommand{" +
                "  type=" + type +
                ", name='" + name + '\'' +
                ", arguments=" + arguments +
                (steps == null ? "" : ", steps=" + steps) +
                ", parameters=" + parameters +
                '}';
    }
}
