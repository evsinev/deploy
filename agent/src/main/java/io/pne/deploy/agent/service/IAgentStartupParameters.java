package io.pne.deploy.agent.service;

import java.io.File;

public interface IAgentStartupParameters {

    String getServerBaseUrl();

    String getAgentId();

    /** Where this host states what a step plan may do. A missing file means step plans are refused. */
    File getPolicyFile();

}
