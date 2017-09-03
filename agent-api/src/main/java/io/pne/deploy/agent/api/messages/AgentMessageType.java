package io.pne.deploy.agent.api.messages;

public enum AgentMessageType {


      RUN_COMMAND_REQUEST  (1, RunAgentCommandRequest.class)
    , RUN_COMMAND_RESPONSE (2, RunAgentCommandResponse.class)
    , RUN_COMMAND_LOG      (3, RunAgentCommandLog.class    )
    ;

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

    public static AgentMessageType findByClass(Class<?> aClass) {
        for (AgentMessageType type : values()) {
            if(aClass.equals(type.clazz)) {
                return type;
            }
        }
        throw new IllegalStateException("Couldn't find type by class " + aClass);
    }
}
