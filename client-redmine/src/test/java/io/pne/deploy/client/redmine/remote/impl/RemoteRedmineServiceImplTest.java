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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileReader;
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

        File aliasDir = new File("../server/src/test/resources/aliases");
        IDeployService deployService = new DeployServiceImpl(
                new AgentFinderServiceImpl(new LocalAgentServiceImpl((aCommandId, aText) -> LOG.info("{}: {}", aCommandId, aText)))
                , aliasDir
        );
        IRedmineIssuesProcessService processService = new RedmineIssuesProcessServiceImpl(redmine, deployService, config);

        processService.processRedmineIssues();
    }

    @Test
    @Ignore
    public void getIssue() throws Exception {
        for (ScriptEngineFactory factory : new ScriptEngineManager().getEngineFactories()) {
            System.out.println("factory = " + factory.getEngineName());
        }
        
        IRedmineRemoteConfig     config  = new RedmineRemoveConfigBuilder().build();
        RemoteRedmineServiceImpl redmine = new RemoteRedmineServiceImpl(config);
        RedmineIssue             issue   = redmine.getIssue(Long.getLong("REDMINE_ISSUE_ID"));
        System.out.println("issue = " + issue);

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        engine.put("issue", issue);

        try (FileReader in = new FileReader(config.issueValidationScript())) {
            Object eval = engine.eval(in);
            System.out.println("eval = " + eval.getClass());
            System.out.println("eval = " + eval);
        }
    }


}