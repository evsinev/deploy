package io.pne.deploy.client.remote;

import java.io.IOException;
import java.io.Writer;

public interface IDeployRemoteService {

    void runCommand(
              String aIssueId
            , String aCommand
    ) throws IOException;
}
