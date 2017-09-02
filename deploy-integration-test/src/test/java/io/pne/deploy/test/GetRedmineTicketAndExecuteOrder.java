package io.pne.deploy.test;

import io.pne.deploy.server.ServerApplication;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GetRedmineTicketAndExecuteOrder {

    Executor executor = Executors.newCachedThreadPool();

    @Test
    public void test() throws InterruptedException {
        TestRedmineServer redmine = new TestRedmineServer();
        redmine.start();

        TestServerListener serverListener = new TestServerListener();
        ServerApplication server = new ServerApplication(serverListener);
        executor.execute(server::start);

        serverListener.awaitStarted();

        server.checkRedmineIssues();


        redmine.stop();
    }
}
