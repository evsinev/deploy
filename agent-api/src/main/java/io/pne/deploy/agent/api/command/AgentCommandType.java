package io.pne.deploy.agent.api.command;

public enum  AgentCommandType {

    /** Legacy: run an executable with arguments on the agent host. */
    SHELL,

    /** Run a plan of typed steps inside the agent process, subject to the agent policy. */
    STEPS
}
