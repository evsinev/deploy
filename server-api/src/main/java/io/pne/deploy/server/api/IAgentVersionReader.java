package io.pne.deploy.server.api;

/**
 * Reads the current application version from a URL that only the agent (not the firewalled deploy-server) can
 * reach. Returns {@code null} when it can't be read.
 */
public interface IAgentVersionReader {

    String readVersion(String aAgentId, String aVersionUrl);
}
