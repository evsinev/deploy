package io.pne.deploy.client.redmine.remote.impl;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.payneteasy.startup.parameters.StartupParametersFactory.getStartupParameters;

public class RemoteTelegramServiceImplTest {
    private final RemoteTelegramServiceImpl telegramService = new RemoteTelegramServiceImpl(getStartupParameters(IRedmineRemoteConfig.class));

    @Test
    @Ignore
    public void sendMessage() {
        telegramService.sendMessage(getMessage());
    }

    @Test
    @Ignore
    public void sendMessages() {
        telegramService.sendMessages(getMessages());
    }

    //Fill list for tests
    private List<String> getMessages() {
        return Arrays.asList("");
    }

    //Fill string for tests
    private String getMessage() {
        return "";
    }
}
