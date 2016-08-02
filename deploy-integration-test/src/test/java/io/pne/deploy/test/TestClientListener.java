package io.pne.deploy.test;

import io.pne.deploy.client.IClientListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestClientListener implements IClientListener {

    ArrayBlockingQueue<String> lines = new ArrayBlockingQueue<>(100);

    @Override
    public void didReceiveLine(String aLine) {
        lines.add(aLine);
    }

    public String awaitLine() throws InterruptedException {
        String line = lines.poll(2, TimeUnit.SECONDS);
        if(line == null) {
            throw new IllegalStateException("Could not get next line in 2 seconds");
        }
        return line;
    }
}
