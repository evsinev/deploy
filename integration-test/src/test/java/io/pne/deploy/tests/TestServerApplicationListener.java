package io.pne.deploy.tests;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import io.pne.deploy.agent.api.messages.RunAgentCommandResponse;
import io.pne.deploy.server.IServerApplicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestServerApplicationListener implements IServerApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestServerApplicationListener.class);

    private final    CountDownLatch            startedLatch = new CountDownLatch(1);
    private volatile Throwable                 startedError = null;
    private final    List<IAgentClientMessage> received     = Collections.synchronizedList(new ArrayList<>());

    /** @return true if the server signalled startup before the timeout. */
    public boolean waitUntilStarted(long aTimeout, TimeUnit aUnit) throws InterruptedException {
        LOG.debug("Waiting for starting ...");
        boolean reached = startedLatch.await(aTimeout, aUnit);
        if (startedError != null) {
            throw new IllegalStateException("Couldn't start ", startedError);
        }
        return reached;
    }

    /** Blocks until at least {@code count} command responses arrive, or the timeout elapses. */
    public List<RunAgentCommandResponse> awaitResponses(int count, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        List<RunAgentCommandResponse> responses = responses();
        while (responses.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(20);
            responses = responses();
        }
        return responses;
    }

    public List<RunAgentCommandResponse> responses() {
        synchronized (received) {
            return received.stream()
                    .filter(m -> m instanceof RunAgentCommandResponse)
                    .map(m -> (RunAgentCommandResponse) m)
                    .collect(Collectors.toList());
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
        received.add(aMessage);
    }

    @Override
    public void didStartFailed(Throwable cause) {
        LOG.debug("didStartFailed: {}", cause);
        startedError = cause;
        startedLatch.countDown();
    }
}
