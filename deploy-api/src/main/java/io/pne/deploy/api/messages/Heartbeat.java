package io.pne.deploy.api.messages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize  (as = ImmutableHeartbeat.class)
@JsonDeserialize(as = ImmutableHeartbeat.class)
public interface Heartbeat {
    String requestId();
}
