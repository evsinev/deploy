package io.pne.deploy.tests;

import io.pne.deploy.agent.service.IAgentStartupParameters;

public class TestAgentStartupParameters implements IAgentStartupParameters {

    private final String url;
    private final String agentName;

    public TestAgentStartupParameters(String url, String agentName) {
        this.url = url;
        this.agentName = agentName;
    }

    @Override
    public String getServerBaseUrl() {
        return url;
    }

    @Override
    public String getAgentId() {
        return agentName;
    }
}
