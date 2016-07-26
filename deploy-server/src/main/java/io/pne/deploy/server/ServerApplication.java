package io.pne.deploy.server;

import io.vertx.core.Vertx;

import java.io.IOException;

public class ServerApplication {

    public static void main(String[] args) throws IOException {

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WebSocketVerticle());

        System.out.println("Press ENTER to exit...");
        System.in.read();

        System.out.println("Exiting ...");
        vertx.close();

    }

}
