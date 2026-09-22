package io.pne.deploy.agent.steps.impl;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** A loopback HTTP server for the tests, so the fetching steps can be exercised without a network. */
public class TestHttpServer implements AutoCloseable {

    private final HttpServer server;

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.start();
    }

    public String url(String aPath) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + aPath;
    }

    public void respond(String aPath, int aStatus, String aBody) {
        byte[] body = aBody.getBytes(StandardCharsets.UTF_8);
        server.createContext(aPath, exchange -> {
            exchange.sendResponseHeaders(aStatus, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    /** Answers with a redirect, which a step must refuse rather than follow to a host nobody allowed. */
    public void redirect(String aPath, String aLocation) {
        server.createContext(aPath, exchange -> {
            exchange.getResponseHeaders().add("Location", aLocation);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    /** Answers with a different body on each call, which is how a restarting application is simulated. */
    public void respondInTurn(String aPath, String... aBodies) {
        int[] call = {0};
        server.createContext(aPath, exchange -> {
            String text = aBodies[Math.min(call[0]++, aBodies.length - 1)];
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
