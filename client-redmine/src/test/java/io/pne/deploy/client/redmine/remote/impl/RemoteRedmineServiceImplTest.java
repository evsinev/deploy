package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.FileReader;

public class RemoteRedmineServiceImplTest {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteRedmineServiceImplTest.class);

    @Test
    @Ignore
    public void getIssue() throws Exception {
        for (ScriptEngineFactory factory : new ScriptEngineManager().getEngineFactories()) {
            System.out.println("factory = " + factory.getEngineName());
        }

        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
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