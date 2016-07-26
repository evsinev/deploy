package io.pne.deploy.api;

import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.messages.HeartbeatAck;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptResult;
import io.pne.deploy.api.tasks.ShellScriptStatus;
import io.pne.deploy.api.tasks.ShellScriptStatusReply;

import java.util.HashMap;
import java.util.Map;

public class MessageTypes {

    private static final Map<Class, Integer> typeToId;
    private static final Map<Integer, Class> idToType;

    static {
        typeToId = new HashMap<>();
        idToType = new HashMap<>();

        add(typeToId, idToType, 0, Heartbeat.class);
        add(typeToId, idToType, 1, HeartbeatAck.class);
        add(typeToId, idToType, 2, ShellScriptParameters.class);
        add(typeToId, idToType, 3, ShellScriptResult.class);
        add(typeToId, idToType, 4, ShellScriptStatus.class);
        add(typeToId, idToType, 5, ShellScriptStatusReply.class);
    }

    private static void add(Map<Class, Integer> typeToId, Map<Integer, Class> idToType, int aId, Class aType) {
        typeToId.put(aType, aId);
        idToType.put(aId, aType);
    }

    public static int findTypeId(Class aType) {
        Integer id = typeToId.get(aType.getInterfaces()[0]);
        if(id == null) {
            throw new IllegalStateException("Unknown type " + aType);
        }
        return id;
    }

    public static Class findType(int aTypeId) {
        Class type = idToType.get(aTypeId);
        if(type == null) {
            throw new IllegalStateException("Unknown type " + aTypeId);
        }
        return type;
    }
}
