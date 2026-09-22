package io.pne.deploy.agent.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command line wrapper around {@link VersionChecks#checkNotOlder}, kept for plans that still run this as a process.
 */
public class CheckVersionCommand {

    private static final Logger LOG = LoggerFactory.getLogger(CheckVersionCommand.class);

    public static void main(String[] args) {
        String versionUrl = args[0];
        String newVersion = args[1];

        try {
            VersionChecks.checkNotOlder(versionUrl, newVersion, LOG::info);
        } catch (Exception e) {
            LOG.error("FAILED: {}", e.getMessage());
            System.exit(1);
        }
    }
}
