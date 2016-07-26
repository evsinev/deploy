package io.pne.deploy.api.tasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Optional;

@Value.Immutable
@JsonSerialize  (as = ImmutableShellScriptParameters.class)
@JsonDeserialize(as = ImmutableShellScriptParameters.class)
public interface ShellScriptParameters {

    public static final String USERNAME_NON_ROOT = "non-root";

    String                          taskId();

    /**
     * If 'non-root' then service checks if user is not a root
     * @return
     */
    String                          username();

    String                          filename();

    Optional<String>                group();
    Optional<String[]>              arguments();
    Optional<Map<String, String>>   environment();

}
