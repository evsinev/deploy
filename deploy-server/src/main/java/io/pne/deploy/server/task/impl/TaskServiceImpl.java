package io.pne.deploy.server.task.impl;

import io.pne.deploy.server.dao.ITasksDao;
import io.pne.deploy.server.redmine.IRedmineService;
import io.pne.deploy.server.task.ITaskService;
import io.pne.deploy.server.task.Task;

import java.util.List;

public class TaskServiceImpl implements ITaskService {

    IRedmineService redmineService;
    ITasksDao       tasksDao;


    @Override
    public void fetchAndRun() {
        List<Task> tasks = redmineService.getAssignedTasks();
        for (Task task : tasks) {
            if(tasksDao.hasTasksForIssue(task.issueId())) {

            }
        }
    }
}
