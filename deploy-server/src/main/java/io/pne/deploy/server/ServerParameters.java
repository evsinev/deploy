package io.pne.deploy.server;

import com.beust.jcommander.Parameter;

public class ServerParameters {

    @Parameter(names = "--port", description = "Server port", descriptionKey = "port")
    public int port = 9020;

    @Override
    public String toString() {
        return "ServerParameters{" +
                "port=" + port +
                '}';
    }
}
