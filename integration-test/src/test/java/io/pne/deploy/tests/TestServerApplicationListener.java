package io.pne.deploy.tests;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import io.pne.deploy.server.IServerApplicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestServerApplicationListener implements IServerApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestServerApplicationListener.class);

    private final   CountDownLatch startedLatch = new CountDownLatch(1);
    private volatile Throwable startedError = null;

    public void waitUntilStarted(long aTimeout, TimeUnit aUnit) throws InterruptedException {
        LOG.debug("Waiting for starting ...");
        startedLatch.await(aTimeout, aUnit);
        if(startedError != null) {
            throw new IllegalStateException("Couldn't start ", startedError);
        }
    }

    @Override
    public void serverStopped() {
        LOG.debug("serverStopped");
    }

    @Override
    public void didStarted() {
        LOG.debug("didStarted");
        startedLatch.countDown();
    }

    @Override
    public <T extends IAgentClientMessage> void didReceiveMessage(T aMessage) {
        LOG.debug("didReceiveMessage: {}", aMessage);

    }

    @Override
    public void didStartFailed(Throwable cause) {
        LOG.debug("didStartFailed: {}", cause);
        startedError = cause;
        startedLatch.countDown();;
    }
}
