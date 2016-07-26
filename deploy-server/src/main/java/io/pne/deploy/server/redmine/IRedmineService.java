package io.pne.deploy.server.redmine;

import io.pne.deploy.server.task.Task;

import java.util.List;

public interface IRedmineService {
    List<Task> getAssignedTasks();
}
