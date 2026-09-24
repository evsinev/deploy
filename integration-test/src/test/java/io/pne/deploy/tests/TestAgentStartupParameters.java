package io.pne.deploy.tests;

import io.pne.deploy.agent.service.IAgentStartupParameters;

import java.io.File;

public class TestAgentStartupParameters implements IAgentStartupParameters {

    private final String url;
    private final String agentName;
    private final File   policyFile;

    public TestAgentStartupParameters(String url, String agentName) {
        this(url, agentName, new File("no-such-policy.yml"));
    }

    public TestAgentStartupParameters(String url, String agentName, File policyFile) {
        this.url = url;
        this.agentName = agentName;
        this.policyFile = policyFile;
    }

    @Override
    public String getServerBaseUrl() {
        return url;
    }

    @Override
    public String getAgentId() {
        return agentName;
    }

    @Override
    public File getPolicyFile() {
        return policyFile;
    }
}
