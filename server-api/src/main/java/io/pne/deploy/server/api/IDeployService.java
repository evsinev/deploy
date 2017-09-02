package io.pne.deploy.server.api;

import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;

/**
 *
 */
public interface IDeployService {

    void runTask(Task aTask) throws TaskException;

}
