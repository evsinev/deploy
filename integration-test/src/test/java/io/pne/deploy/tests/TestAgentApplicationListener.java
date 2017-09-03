package io.pne.deploy.tests;

import io.pne.deploy.agent.api.messages.IAgentServerMessage;
import io.pne.deploy.agent.service.IAgentApplicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestAgentApplicationListener implements IAgentApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestAgentApplicationListener.class);

    private final    CountDownLatch startedLatch = new CountDownLatch(1);
    private volatile Throwable      startedError = null;

    @Override
    public void didConnected() {
        LOG.debug("didConnected");
        startedLatch.countDown();
    }

    @Override
    public <T extends IAgentServerMessage> void didReceiveMessage(T aMessage) {
        LOG.debug("didReceiveMessage: {}", aMessage);
    }

    public void waitUntilConnected(long aTimeout, TimeUnit aUnit) throws InterruptedException {
        LOG.debug("Waiting for connection ...");
        startedLatch.await(aTimeout, aUnit);
        if(startedError != null) {
            throw new IllegalStateException("Couldn't connect ", startedError);
        }
        LOG.debug("Connected");
    }
}
