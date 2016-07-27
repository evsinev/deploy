package io.pne.deploy.api.tasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.pne.deploy.api.IClientMessage;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize  (as = ImmutableShellScriptLog.class)
@JsonDeserialize(as = ImmutableShellScriptLog.class)
public interface ShellScriptLog extends IClientMessage{

    String message();
    String taskId();
}
