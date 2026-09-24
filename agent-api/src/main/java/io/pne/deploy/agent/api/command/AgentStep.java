package io.pne.deploy.agent.api.command;

import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One typed action of a deploy plan, as it travels over the wire.
 *
 * <p>The representation is deliberately flat - a type discriminator plus string parameters - so that Gson needs no
 * polymorphic adapter and both sides agree on the schema by looking at the type alone. The sender resolves every
 * parameter to a string before sending; the receiver parses and validates the parameters it knows for that type.
 *
 * <p>Instances may come from Gson, which writes the final fields reflectively and can leave them {@code null} when
 * the JSON omits them. Read them through {@link #getType()} / {@link #getParams()}, which never return {@code null}.
 */
public class AgentStep {

    public final String              type;
    public final Map<String, String> params;

    public AgentStep(@Nonnull String aType, @Nonnull Map<String, String> aParams) {
        type   = aType;
        params = new LinkedHashMap<>(aParams);
    }

    @Nonnull
    public String getType() {
        return type == null ? "" : type;
    }

    @Nonnull
    public Map<String, String> getParams() {
        return params == null ? Collections.emptyMap() : params;
    }

    @Override
    public String toString() {
        return getType() + getParams();
    }
}
