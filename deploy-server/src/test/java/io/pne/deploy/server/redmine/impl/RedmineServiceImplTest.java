package io.pne.deploy.server.redmine.impl;

import io.pne.deploy.server.dao.IIssuesDao;
import io.pne.deploy.server.dao.impl.IssueDaoImpl;
import io.pne.deploy.server.model.Order;
import io.pne.deploy.server.redmine.*;
import io.pne.deploy.server.service.redmine.*;
import io.pne.deploy.server.service.redmine.impl.RedmineServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class RedmineServiceImplTest {

    @Test
    public void test() {
        IRedmineRemoteService redmineRemoteService = this::createTickets;

        IIssuesDao issuesDao  = new IssueDaoImpl(new File("./target"));
        IRedmineService redmineService = new RedmineServiceImpl(redmineRemoteService, issuesDao);
        List<Order> orders = redmineService.processAssignedTickets();
        Assert.assertEquals(1, orders.size());
        Order order = orders.get(0);
        Assert.assertEquals("123", order.issue);
        Assert.assertEquals(1, order.commands.size());
        Assert.assertEquals("@deploy", order.commands.get(0).commandName);
        Assert.assertEquals(2, order.commands.get(0).parameters.size());

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
                        .commentId(1)
                        .build()
                )
                .addComments(ImmutableRedmineComment.builder()
                        .text("OK")
                        .userId(5)
                        .commentId(2)
                        .build()
                )
                .addComments(ImmutableRedmineComment.builder()
                        .text("OK")
                        .userId(8)
                        .commentId(3)
                        .build()
                )
                .build();
        return Collections.singletonList(ticket);
    }

}
