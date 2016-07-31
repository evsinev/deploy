package io.pne.deploy.client.remote.impl;

import io.pne.deploy.client.remote.IDeployRemoteService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Writer;

public class DeployRemoteServiceImpl implements IDeployRemoteService {

    private static final Logger LOG = LoggerFactory.getLogger(DeployRemoteServiceImpl.class);

    private final String baseUrl;

    OkHttpClient client = new OkHttpClient();

    public DeployRemoteServiceImpl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void runCommand(String aIssueId, String aCommand) throws IOException {

        RequestBody body = new FormBody.Builder()
                .add("issue"  , aIssueId)
                .add("command", aCommand)
                .build();

        Request request = new Request.Builder()
                .url(baseUrl+"/run-command")
                .post(body)
                .build();

        Response response = client.newCall(request).execute();

        try (LineNumberReader in = new LineNumberReader(response.body().charStream())) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
        }

    }
}
