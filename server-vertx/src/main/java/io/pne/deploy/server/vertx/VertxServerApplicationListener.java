package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import io.pne.deploy.client.redmine.process.IRedmineIssuesProcessService;
import io.pne.deploy.server.IServerApplicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VertxServerApplicationListener implements IServerApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(VertxServerApplicationListener.class);

    private final    IRedmineIssuesProcessService redmineIssuesProcessService;
    private volatile Thread                       thread;
    private final   ArrayBlockingQueue<Long>      pendingIssues;

    public VertxServerApplicationListener(IRedmineIssuesProcessService aRedmineProcessService, ArrayBlockingQueue<Long> aIssues) {
        redmineIssuesProcessService = aRedmineProcessService;
        pendingIssues = aIssues;
    }

    @Override
    public void serverStopped() {
        thread.interrupt();
    }

    @Override
    public void didStarted() {
        thread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    Long issueId = pendingIssues.poll(60, TimeUnit.SECONDS);
                    if(issueId == null) {
                        continue;
                    }

                    redmineIssuesProcessService.processRedmineIssue(issueId);

                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    LOG.info("Interrupted", e);
                    break;
                } catch (Exception e) {
                    LOG.error("Error while getting issue", e);
                }
            }
        });
        thread.setName("redmine-processing");
        thread.start();
    }

    @Override
    public <T extends IAgentClientMessage> void didReceiveMessage(T aMessage) {

    }

    @Override
    public void didStartFailed(Throwable cause) {
    }
}
