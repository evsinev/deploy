package io.pne.deploy.server;

import com.beust.jcommander.Parameter;

public class ServerParameters {

    @Parameter(names = "--port", description = "Server port", descriptionKey = "port")
    public int port = 9020;

    @Parameter(names = "--redmine-url", description = "Redmine base url", descriptionKey = "redmine-url", required = true)
    public String redmineUrl;

    @Parameter(names = "--redmine-access-key", description = "Redmine API Access key", descriptionKey = "redmine-access-key", required = true)
    public String redmineAccessKey;

    @Parameter(names = "--issues-dir", description = "Issues dir", descriptionKey = "issues-dir")
    public String issuesDir = "./issues";


    @Override
    public String toString() {
        return "ServerParameters{" +
                "port=" + port +
                '}';
    }
}
