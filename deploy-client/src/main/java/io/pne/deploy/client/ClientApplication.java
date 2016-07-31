package io.pne.deploy.client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.pne.deploy.client.remote.IDeployRemoteService;
import io.pne.deploy.client.remote.impl.DeployRemoteServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ClientApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ClientApplication.class);

    private final ClientParameters parameters;
    IDeployRemoteService deployService;

    public ClientApplication(ClientParameters parameters) {
        this.parameters = parameters;
        deployService = new DeployRemoteServiceImpl(parameters.server);
    }

    public void runCommand() throws IOException {
        deployService.runCommand(parameters.issue, parameters.command);
    }

    public static void main(String[] args) throws IOException {

        // --server  http://localhost:9020/deploy
        // --issue   1234
        // --command "@test.sh 127.0.0.1 key=value"

        // /deploy/queue
        // /deploy/log/issue/1234

        ClientParameters parameters = new ClientParameters();
        try {
            new JCommander(parameters, args);
            LOG.info("Parameters: {}", parameters);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println();
            StringBuilder sb = new StringBuilder();
            new JCommander(parameters).usage(sb);
            System.err.println(sb);
            return;
        }

        ClientApplication app = new ClientApplication(parameters);
        app.runCommand();

    }
}
