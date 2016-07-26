package io.pne.deploy.server.redmine.impl;

import io.pne.deploy.server.redmine.IRedmineRemoteService;
import io.pne.deploy.server.redmine.IRedmineService;
import io.pne.deploy.server.redmine.RedmineIssue;
import io.pne.deploy.server.task.ImmutableTask;
import io.pne.deploy.server.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RedmineServiceImpl implements IRedmineService {

    private final Set<Long> users;
    private final IRedmineRemoteService redmineRemoteService;

    public RedmineServiceImpl(IRedmineRemoteService aRemoteService, Set<Long> aUsers) {
        redmineRemoteService = aRemoteService;
        users = aUsers;
    }


    @Override
    public List<Task> getAssignedTasks() {
        List<Task> tasks = new ArrayList<>();
        for (RedmineIssue issue : redmineRemoteService.listAssignedTickets()) {
            IssueToTaskConverter converter = new IssueToTaskConverter(users, issue);
            if(converter.isIssueApproved()) {
                Task task = ImmutableTask.builder()
                        .hosts      ( converter.getHosts()      )
                        .command    ( converter.getCommand()    )
                        .parameters ( converter.getParameters() )
                        .issueId    ( issue.issueId()           )
                        .build();
                tasks.add(task);
            }
        }
        return tasks;
    }
}
