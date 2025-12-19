package io.pne.deploy.client.redmine.process;

import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.server.api.task.Task;

import java.util.List;

public interface DiffService {
    List<DiffTask> getCurrentVersion(Task task);
    void processDiff(List<DiffTask> tasks, int issueId);
}
