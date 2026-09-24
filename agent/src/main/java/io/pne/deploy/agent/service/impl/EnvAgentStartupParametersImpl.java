package io.pne.deploy.agent.service.impl;

import io.pne.deploy.agent.service.IAgentStartupParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.io.File;

public class EnvAgentStartupParametersImpl implements IAgentStartupParameters {

    private static final String DEFAULT_POLICY_FILE = "./etc/policy.yml";

    @Nonnull private final String serverBaseUrl;
    @Nonnull private final String agentId;
    @Nonnull private final File   policyFile;

    private static final Logger LOG = LoggerFactory.getLogger(EnvAgentStartupParametersImpl.class);

    public EnvAgentStartupParametersImpl() {
        serverBaseUrl = getRequired("SERVER_BASE_URL");
        agentId = getRequired("AGENT_ID");
        policyFile = new File(getOptional("AGENT_POLICY_FILE", DEFAULT_POLICY_FILE));

        LOG.info("Parameters:");
        LOG.info("    SERVER_BASE_URL   = {}", serverBaseUrl);
        LOG.info("    AGENT_ID          = {}", agentId);
        LOG.info("    AGENT_POLICY_FILE = {}", policyFile);
    }

    private String getRequired(String aName) {
        String value = System.getenv(aName);
        if(value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException("No environment variable " + aName);
        }
        return value;
    }

    private String getOptional(String aName, String aDefault) {
        String value = System.getenv(aName);
        return value == null || value.trim().length() == 0 ? aDefault : value.trim();
    }

    @Override
    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public File getPolicyFile() {
        return policyFile;
    }
}
