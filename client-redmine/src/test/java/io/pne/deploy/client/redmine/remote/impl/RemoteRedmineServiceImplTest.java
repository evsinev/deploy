package io.pne.deploy.client.redmine.remote.impl;

import io.pne.deploy.client.redmine.process.IRedmineIssuesProcessService;
import io.pne.deploy.client.redmine.process.impl.RedmineIssuesProcessServiceImpl;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import io.pne.deploy.server.agent.impl.AgentFinderServiceImpl;
import io.pne.deploy.server.agent.impl.LocalAgentServiceImpl;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.service.impl.DeployServiceImpl;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RemoteRedmineServiceImplTest {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteRedmineServiceImplTest.class);

    @Test
    @Ignore
    public void listAssignedTickets() throws Exception {

        IRedmineRemoteConfig config = new RedmineRemoveConfigBuilder().build();
        RemoteRedmineServiceImpl redmine = new RemoteRedmineServiceImpl(config);

        List<RedmineIssue> issues = redmine.listAssignedTickets();

        LOG.info("Issues count: " + issues.size());
        for (RedmineIssue issue : issues) {
            LOG.info("{} {} ({}) {}", issue.issueId(), issue.statusName(), issue.statusId(), issue.subject());
        }
    }

    @Test
    @Ignore
    public void processTask() throws Exception {

        IRedmineRemoteConfig config = new RedmineRemoveConfigBuilder().build();
        RemoteRedmineServiceImpl redmine = new RemoteRedmineServiceImpl(config);

        IDeployService deployService = new DeployServiceImpl(new AgentFinderServiceImpl(new LocalAgentServiceImpl((aCommandId, aText) -> LOG.info("{}: {}", aCommandId, aText))));
        IRedmineIssuesProcessService processService = new RedmineIssuesProcessServiceImpl(redmine, deployService);

        processService.processRedmineIssues();
    }

}