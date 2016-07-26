package io.pne.deploy.server.dao;

import io.pne.deploy.server.dao.impl.TaskState;
import org.immutables.value.Value;

@Value.Immutable
public interface TTask {

    String    taskId();

    TaskState state();
}
