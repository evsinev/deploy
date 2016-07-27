package io.pne.deploy.agent;

import com.beust.jcommander.Parameter;

public class AgentParameters {

    @Parameter(names = "--server", description = "Server URL")
    public String serverUrl = "http://localhost:9020/";
}
