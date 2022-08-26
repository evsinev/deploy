package io.pne.deploy.server.vertx.status.model;


import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Data
@Builder
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class DeployStatus {
    Set<String>      connectedAgents;
    Collection<Long> issueQueue;
    TaskStatus       taskStatus;
}
