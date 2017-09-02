package io.pne.deploy.agent.api.messages;

import io.pne.deploy.agent.api.command.AgentCommand;

public enum AgentMessageType {


    RUN_TASK(1, AgentCommand.class);

    public final byte id;
    public final Class clazz;

    AgentMessageType(int id, Class clazz) {
        this.id = (byte) id;
        this.clazz = clazz;
    }

    public static AgentMessageType findType(byte aTypeId) {
        for (AgentMessageType type : values()) {
            if(aTypeId == type.id) {
                return type;
            }
        }
        throw new IllegalStateException("Couldn't find type by id " + aTypeId);
    }

    public static AgentMessageType findByClass(Class<? extends IAgentServerMessage> aClass) {
        for (AgentMessageType type : values()) {
            if(aClass.equals(type.clazz)) {
                return type;
            }
        }
        throw new IllegalStateException("Couldn't find type by class " + aClass);
    }
}
