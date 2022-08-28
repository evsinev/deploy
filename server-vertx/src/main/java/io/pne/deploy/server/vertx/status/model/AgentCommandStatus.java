package io.pne.deploy.server.vertx.status.model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Builder
@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class AgentCommandStatus {
}
