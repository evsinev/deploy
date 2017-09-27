package io.pne.deploy.server.vertx;

import io.pne.deploy.agent.api.messages.IAgentClientMessage;
import io.pne.deploy.client.redmine.process.IRedmineIssuesProcessService;
import io.pne.deploy.server.IServerApplicationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxServerApplicationListener implements IServerApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(VertxServerApplicationListener.class);

    private final    IRedmineIssuesProcessService redmineIssuesProcessService;
    private volatile Thread                       thread;

    public VertxServerApplicationListener(IRedmineIssuesProcessService aRedmineProcessService) {
        redmineIssuesProcessService = aRedmineProcessService;
    }

    @Override
    public void serverStopped() {
        thread.interrupt();
    }

    @Override
    public void didStarted() {
        thread = new Thread(() -> {
            while(!Thread.currentThread().isInterrupted()) {
                LOG.info("Getting redmine tickets ...");
                try {
                    redmineIssuesProcessService.processRedmineIssues();
                } catch (Exception e) {
                    LOG.info("Error processing tickets", e);
                    try {
                        LOG.warn("Sleeping for 10 seconds while redmine will fill better.");
                        Thread.sleep(600_00);
                    } catch (InterruptedException e1) {
                        LOG.info("Interrupted", e);
                        break;
                    }
                }

                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    LOG.info("Interrupted", e);
                    break;
                }
            }
        });
        thread.setName("redmine-fetcher");
        thread.start();
    }

    @Override
    public <T extends IAgentClientMessage> void didReceiveMessage(T aMessage) {

    }

    @Override
    public void didStartFailed(Throwable cause) {
    }
}
