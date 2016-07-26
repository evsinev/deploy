package io.pne.deploy.server.dao.impl;

import io.pne.deploy.server.dao.ITasksDao;

public class TaskDaoImpl implements ITasksDao {

    @Override
    public boolean hasTasksForIssue(long aIssueId) {
        return true;
    }
}
