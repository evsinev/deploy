package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.service.IAgentStartupParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class EnvAgentStartupParametersImpl implements IAgentStartupParameters {

    @Nonnull private final String serverBaseUrl;
    @Nonnull private final String agentId;

    private static final Logger LOG = LoggerFactory.getLogger(EnvAgentStartupParametersImpl.class);

    public EnvAgentStartupParametersImpl() {
        serverBaseUrl = getRequired("SERVER_BASE_URL");
        agentId = getRequired("AGENT_ID");

        LOG.info("Parameters:");
        LOG.info("    SERVER_BASE_URL = {}", serverBaseUrl);
        LOG.info("    AGENT_ID        = {}", agentId);
    }

    private String getRequired(String aName) {
        String value = System.getenv(aName);
        if(value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("No environment variable " + aName);
        }
        return value;
    }

    @Override
    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    @Override
    public String getAgentId() {
        return agentId;
    }
}
