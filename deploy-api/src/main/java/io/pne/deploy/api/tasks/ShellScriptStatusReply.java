package io.pne.deploy.api.tasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableShellScriptParameters.class)
@JsonDeserialize(as = ImmutableShellScriptParameters.class)
public interface ShellScriptStatusReply {
}
