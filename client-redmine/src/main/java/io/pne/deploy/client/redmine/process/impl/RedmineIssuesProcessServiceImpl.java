package io.pne.deploy.client.redmine.process.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.pne.deploy.client.redmine.process.IRedmineIssuesProcessService;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.FileReader;
import java.util.List;
import java.util.Scanner;

public class RedmineIssuesProcessServiceImpl implements IRedmineIssuesProcessService {

    private static final Logger LOG = LoggerFactory.getLogger(RedmineIssuesProcessServiceImpl.class);

    private final IRemoteRedmineService redmine;
    private final IDeployService        deployService;
    private final Gson                  gson   = new GsonBuilder().setPrettyPrinting().create();
    private final ScriptEngine          engine = new ScriptEngineManager().getEngineByName("nashorn");
    private final String                issueValidationScript;


    public RedmineIssuesProcessServiceImpl(IRemoteRedmineService redmine, IDeployService deployService, IRedmineRemoteConfig aConfig) {
        this.redmine = redmine;
        this.deployService = deployService;
        issueValidationScript = aConfig.issueValidationScript();
    }

    @Override
    public void processRedmineIssues() {
        List<RedmineIssue> issues = redmine.listAssignedTickets();
        for (RedmineIssue issue : issues) {
            try {
                processIssue(issue);
            } catch (Exception e) {
                LOG.error("Can't process issue " + issue.issueId(), e);
                redmine.changeStatusToFailed(issue.issueId(), "Task is FAILED: " + e.getMessage());
            }
        }
    }

    @Override
    public void processRedmineIssue(long aIssueId) {
        RedmineIssue issue = redmine.getIssue(aIssueId);
        engine.put("issue", issue);
        try {
            try (FileReader in = new FileReader(issueValidationScript)) {
                LOG.info("Checking {} ...", issue);
                Object eval = engine.eval(in);
                LOG.info("Check result is {} {}", eval, eval.getClass());
                Boolean validated = (Boolean) eval;
                if(validated) {
                    processIssue(issue);
                }
            }

        } catch (Exception e) {
            LOG.error("Can't process issue " + issue.issueId(), e);
            redmine.changeStatusToFailed(issue.issueId(), "Task is FAILED: " + e.getMessage());
        }
    }

    private void processIssue(RedmineIssue aIssue) throws Exception {
        Task task = parseTask(aIssue.description());
        redmine.changeStatusFromAcceptedToProcessing(aIssue.issueId(), "Starting task" + formatTask(task));
        deployService.runTask(task);
        redmine.changeStatusToDone(aIssue.issueId(), "Task is DONE");
    }

    private String formatTask(Task aTask) {
        return "\n<pre><code class='json'>"
                + "\n"
                + gson.toJson(aTask)
                + "\n"
                + "</code></pre>";
    }

    protected Task parseTask(String aIssueDescription) throws TaskException {
        Scanner scanner = new Scanner(aIssueDescription);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if(line.startsWith("> deploy")) {
                return createSimpleTask(line);
            }
        }

        throw new IllegalStateException("Can't find a line started with '> deploy' in the Description");
    }

    private Task createSimpleTask(String aLine) throws TaskException {
        String text = aLine.replace("> deploy", "").trim();
        return deployService.parseAlias(text);
    }
}
