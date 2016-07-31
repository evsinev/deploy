package io.pne.deploy.client;

import com.beust.jcommander.Parameter;

public class ClientParameters {

    // --server  http://localhost:9020/deploy
    // --issue   1234
    // --command "@test.sh 127.0.0.1 key=value"

    // /deploy/queue
    // /deploy/log/issue/1234

    @Parameter(names = "--server", description = "Server URL", required = true)
    public String server;

    @Parameter(names = "--issue", description = "Redmine issue number", required = true)
    public String issue;

    @Parameter(names = "--command", description = "Command to execute", required = true)
    public String command;


}
