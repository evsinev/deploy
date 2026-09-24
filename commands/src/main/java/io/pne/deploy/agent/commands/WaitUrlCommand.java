package io.pne.deploy.agent.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command line wrapper around {@link UrlWaiter#waitFor}, kept for plans that still run this as a process.
 */
public class WaitUrlCommand {

    private static final Logger LOG = LoggerFactory.getLogger(WaitUrlCommand.class);

    public static void main(String[] args) throws InterruptedException {
        String versionUrl    = args[0];
        String content       = args[1];
        int    secondsToWait = Integer.parseInt(args[2]);

        try {
            UrlWaiter.waitFor(versionUrl, content, secondsToWait, 1, true, LOG::info);
        } catch (IllegalStateException e) {
            LOG.error("FAILED: {}", e.getMessage());
            System.exit(1);
        }
    }
}
