package io.pne.deploy.server.service.impl.alias;

import java.util.Map;

/** One step as written in a recipe or inline in an alias, before its values are resolved. */
public class AliasStep {

    public String              type;
    public Map<String, Object> params;

    @Override
    public String toString() {
        return type + (params == null ? "{}" : params.toString());
    }
}
