package io.pne.deploy.server;

import io.pne.deploy.api.IServerMessage;
import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.vertx.core.Vertx;

import java.io.IOException;

public class ServerApplication {

    private final IServerListener   listener;
    private final Vertx             vertx;
    private final WebSocketVerticle verticle;

    public ServerApplication(IServerListener aServerListener) {
        listener = aServerListener;
        vertx    = Vertx.vertx();
        verticle = new WebSocketVerticle(aServerListener);
    }

    public void start() {
        vertx.deployVerticle(verticle, event -> {
            listener.didStarted();
        });
    }


    public void stop() {
        System.out.println("Exiting ...");
        vertx.close(event -> {
            listener.serverStopped();
        });
    }

    public void sendMessage(String aHostname, IServerMessage aMessage) {
        verticle.sendMessage(aHostname, aMessage);
    }

    public static void main(String[] args) throws IOException {

        ServerApplication server = new ServerApplication(new ServerListenerNoOp());
        server.start();

        System.out.println("Press ENTER to exit...");
        System.in.read();

        server.stop();



    }

}
