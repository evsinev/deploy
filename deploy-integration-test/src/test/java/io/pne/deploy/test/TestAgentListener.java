package io.pne.deploy.test;

import io.pne.deploy.agent.IAgentListener;
import io.pne.deploy.api.IServerMessage;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestAgentListener implements IAgentListener {

    ArrayBlockingQueue<IServerMessage> messages = new ArrayBlockingQueue<>(100);

    CountDownLatch connectionLatch = new CountDownLatch(1);

    @Override
    public void didConnected() {
        connectionLatch.countDown();
    }

    public void awaitConnected() throws InterruptedException {
        if(!connectionLatch.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Agent did not connected to server during 2 second");
        }
    }

    @Override
    public <T extends IServerMessage> void didReceiveMessage(T aMessage) {
        messages.add(aMessage);
    }

    public <T extends IServerMessage> T awaitMessage() throws InterruptedException {
        //noinspection unchecked
        T message = (T) messages.poll(2, TimeUnit.SECONDS);
        if(message == null) {
            throw new IllegalStateException("Could not get next message in 2 seconds");
        }
        return message;
    }
}
