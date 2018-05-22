package io.pne.deploy.server.vertx.http;

import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.vertx.AgentConnections;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Executor;

public class HttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class);

    private final AgentConnections          connections;
    private final IDeployService            deployService;
    private final Executor                  commandExecutor;
    private final IRedmineRemoteConfig      redmineConfig;
    private final RedmineCallbackHttpHander redmineCallbackHttpHander;
    private final Collection<Long> issues;

    public HttpHandler(AgentConnections connections, IDeployService deployService, Executor commandExecutor, IRedmineRemoteConfig aRedmineConfig, Collection<Long> aIssues) {
        this.connections = connections;
        this.deployService = deployService;
        this.commandExecutor = commandExecutor;
        redmineConfig = aRedmineConfig;
        redmineCallbackHttpHander = new RedmineCallbackHttpHander(aIssues);
        issues = aIssues;
    }

    @Override
    public void handle(HttpServerRequest aRequest) {

        LOG.info("URI is {}", aRequest.uri());

        if(redmineConfig.redmineCallbackUrl().equals(aRequest.uri())) {
            redmineCallbackHttpHander.handle(aRequest);
            return;
        }

        if(aRequest.getParam("command") == null) {
            aRequest.response().setStatusCode(405).end("No command parameter\n");
            return;
        }
        
        HttpServerResponse response = aRequest.response();
        response.setChunked(true);

        aRequest.setExpectMultipart(true);
        aRequest.endHandler(event -> {
            String command = aRequest.getParam("command");
            String result;
            switch (command) {
                case "listAgents":
                    result = connections.getAgents();
                    break;
                case "run":
                    result = runAlias(aRequest.getParam("alias"));
                    break;

                case "issue":
                    long issue_id = Long.parseLong(aRequest.getParam("issue_id"));
                    issues.add(issue_id);
                    result = "Added issue " + issue_id + " to queue";
                    break;

                default:
                    result = "Unknown command: " + command;

            }
            response.write(result);
            response.write("\n").end();
        });

    }

    private String runAlias(String aAlias) {
        StringBuilder sb = new StringBuilder();
        sb.append("alias: ").append(aAlias).append("\n");
        try {
            Task task = deployService.parseAlias(aAlias);
            sb.append("task: ").append(task).append("\n");
            commandExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        deployService.runTask(task);
                    } catch (TaskException e) {
                        LOG.error("Couldn't run task: " + task);
                    }
                }
            });
            return sb.toString();
        } catch (Exception e) {
            LOG.error("Couldn't run alias: " + aAlias, e);
            sb.append(e.getMessage());
            sb.append("\n");
            return sb.toString();
        }
    }
}
