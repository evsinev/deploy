package io.pne.deploy.server.redmine.impl;

import io.pne.deploy.server.redmine.*;
import io.pne.deploy.server.task.Task;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RedmineServiceImplTest {

    @Test
    public void test() {
        IRedmineRemoteService redmineRemoteService = new IRedmineRemoteService() {
            @Override
            public List<RedmineIssue> listAssignedTickets() {
                return createTickets();
            }
        };

        IRedmineService redmineService = new RedmineServiceImpl(redmineRemoteService, createUsers(2, 5, 8));
        List<Task> tasks = redmineService.getAssignedTasks();


    }

    private Set<Long> createUsers(long ... aUsers) {
        Set<Long> users = new HashSet<>();
        for (long user : aUsers) {
            users.add(user);
        }
        return users;
    }

    private List<RedmineIssue> createTickets() {
        // todo use immutable collections
        RedmineIssue ticket = ImmutableRedmineIssue.builder()
                .issueId(123)
                .description("Install new version to sandbox\n" +
                        "@deploy hosts=sandbox-proc version=R3.29.04-123"
                )
                .addComments(ImmutableRedmineComment.builder()
                        .text("OK")
                        .userId(2)
                        .build()
                )
                .addComments(ImmutableRedmineComment.builder()
                        .text("OK")
                        .userId(5)
                        .build()
                )
                .addComments(ImmutableRedmineComment.builder()
                        .text("OK")
                        .userId(8)
                        .build()
                )
                .build();
        return Arrays.asList(ticket);
    }

}
