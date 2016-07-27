package io.pne.deploy.api.messages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.pne.deploy.api.IServerMessage;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize  (as = ImmutableHeartbeat.class)
@JsonDeserialize(as = ImmutableHeartbeat.class)
public interface Heartbeat extends IServerMessage {
    String requestId();
}
