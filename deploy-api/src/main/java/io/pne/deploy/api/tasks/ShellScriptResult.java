package io.pne.deploy.api.tasks;

import io.pne.deploy.api.IClientMessage;
import org.immutables.value.Value;

@Value.Immutable
public interface ShellScriptResult extends IClientMessage {

    int exitCode();

}
