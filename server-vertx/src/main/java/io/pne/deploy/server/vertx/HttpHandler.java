package io.pne.deploy.server.vertx;

import io.pne.deploy.server.api.IDeployService;
import io.pne.deploy.server.api.exceptions.TaskException;
import io.pne.deploy.server.api.task.Task;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

public class HttpHandler implements Handler<HttpServerRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpHandler.class);

    private final AgentConnections connections;
    private final IDeployService   deployService;
    private final Executor         commandExecutor;

    public HttpHandler(AgentConnections connections, IDeployService deployService, Executor commandExecutor) {
        this.connections = connections;
        this.deployService = deployService;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void handle(HttpServerRequest aRequest) {

        if(aRequest.getParam("command") == null) {
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

                default:
                    result = "Unknown command: " + command;

            }
            response.write(result);
            response.write("\n").end();
//            response.close();
//            response.
//            response.close();
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
