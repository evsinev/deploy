package io.pne.deploy.api.messages;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.pne.deploy.api.IClientMessage;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize  (as = ImmutableHeartbeatAck.class)
@JsonDeserialize(as = ImmutableHeartbeatAck.class)
public interface HeartbeatAck extends IClientMessage {

    String responseId();

}
