package io.pne.deploy.test;

import io.pne.deploy.api.IClientMessage;
import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.server.IServerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestServerListener implements IServerListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestServerListener.class);

    ArrayBlockingQueue<IClientMessage> messages     = new ArrayBlockingQueue<>(100);
    CountDownLatch                     startedLatch = new CountDownLatch(1);

    @Override
    public void serverStopped() {
        LOG.info("Server stopped");
    }

    @Override
    public void didStarted() {
        LOG.info("Server started");
        startedLatch.countDown();
    }

    public void awaitStarted() throws InterruptedException {
        if(!startedLatch.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Server did not started 2 second");
        }
    }

    public <T extends IClientMessage> void didReceiveMessage(T aMessage) {
        messages.add(aMessage);
    }

    public <T extends IClientMessage> T awaitMessage() throws InterruptedException {
        //noinspection unchecked
        T message = (T) messages.poll(2, TimeUnit.SECONDS);
        if(message == null) {
            throw new IllegalStateException("Could not get client message in 2 seconds");
        }
        return message;
    }

}
