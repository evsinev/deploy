package io.pne.deploy.test;

import io.pne.deploy.server.service.redmine.IRedmineRemoteService;
import io.pne.deploy.server.service.redmine.RedmineIssue;
import io.pne.deploy.server.service.redmine.impl.RedmineRemoteServiceImpl;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

public class RedmineRemoteServiceImplTest {

    @Test
    @Ignore
    public void test() {

        String uri = System.getenv("REDMINE_URL");
        if(uri == null) {
            Assert.fail("No REDMINE_URL environment variable");
        }
        String apiAccessKey = System.getenv("REDMINE_ACCESS_KEY");
        if(apiAccessKey == null) {
            Assert.fail("No REDMINE_ACCESS_KEY environment variable");
        }

        IRedmineRemoteService redmine = new RedmineRemoteServiceImpl(uri, apiAccessKey);
        List<RedmineIssue> issues = redmine.listAssignedTickets();
        Assert.assertNotNull(issues);

        System.out.println(issues);

    }
}
