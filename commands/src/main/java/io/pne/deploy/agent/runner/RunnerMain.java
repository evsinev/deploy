package io.pne.deploy.agent.runner;

import io.pne.deploy.agent.commands.CheckVersionCommand;
import io.pne.deploy.agent.commands.WaitUrlCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RunnerMain {

    private static final Logger LOG = LoggerFactory.getLogger(RunnerMain.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        LOG.info("Parameters: ");
        for(int i=0; i<args.length; i++) {
            LOG.info("    {} - {}", i, args[i]);
        }

        String name = args[0];
        String[] parameters = createParameters(args);

        switch (name) {
            case "CheckVersion":
                CheckVersionCommand.main(parameters);
                return;

            case "WaitUrl":
                WaitUrlCommand.main(parameters);
                return;

            default:
                LOG.error("Unknown command: {}", name);
                System.exit(1);
        }
    }

    private static String[] createParameters(String[] args) {
        String[] parameters = new String[args.length - 1 ];
        System.arraycopy(args, 1, parameters, 0, parameters.length);
        return parameters;
    }
}
