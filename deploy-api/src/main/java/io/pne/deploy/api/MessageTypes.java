package io.pne.deploy.api;

import io.pne.deploy.api.messages.Heartbeat;
import io.pne.deploy.api.messages.HeartbeatAck;
import io.pne.deploy.api.messages.ImmutableHeartbeat;
import io.pne.deploy.api.messages.ImmutableHeartbeatAck;
import io.pne.deploy.api.tasks.*;

import java.util.HashMap;
import java.util.Map;

public class MessageTypes {

    private static final Map<Class, Integer> typeToId;
    private static final Map<Integer, Class> idToType;

    static {
        typeToId = new HashMap<>();
        idToType = new HashMap<>();

        add(typeToId, idToType, 0, Heartbeat.class              , ImmutableHeartbeat.class);
        add(typeToId, idToType, 1, HeartbeatAck.class           , ImmutableHeartbeatAck.class);
        add(typeToId, idToType, 2, ShellScriptParameters.class  , ImmutableShellScriptParameters.class);
        add(typeToId, idToType, 3, ShellScriptResult.class      , ImmutableShellScriptResult.class    );
        add(typeToId, idToType, 4, ShellScriptStatus.class      , ImmutableShellScriptStatus.class);
        add(typeToId, idToType, 5, ShellScriptStatusReply.class , ImmutableShellScriptStatusReply.class);
        add(typeToId, idToType, 6, ShellScriptLog.class         , ImmutableShellScriptLog.class);
    }

    private static void add(Map<Class, Integer> typeToId, Map<Integer, Class> idToType, int aId, Class aType, Class ... aOtherClasses) {
        typeToId.put(aType, aId);
        idToType.put(aId, aType);
        if(aOtherClasses != null) {
            for (Class clazz : aOtherClasses) {
                typeToId.put(clazz, aId);
            }
        }
    }

    public static int findTypeId(Class aType) {
        Integer id = typeToId.get(aType);
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
