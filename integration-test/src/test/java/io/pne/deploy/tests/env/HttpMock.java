package io.pne.deploy.tests.env;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A tiny recording HTTP mock backed by a raw Vert.x {@link HttpServer}. Every request (method, path,
 * query, body) is recorded; the supplied {@link Responder} writes the reply. Used to stand in for
 * Redmine / GitLab / Telegram in the local integration environment.
 */
public class HttpMock {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMock.class);

    /** Writes the response for a received request (body already fully read). */
    public interface Responder {
        void respond(HttpServerRequest request, String body);
    }

    /** One recorded request. */
    public static final class Rec {
        public final String method;
        public final String path;
        public final String query;
        public final String body;

        Rec(String aMethod, String aPath, String aQuery, String aBody) {
            method = aMethod;
            path   = aPath;
            query  = aQuery;
            body   = aBody;
        }

        @Override
        public String toString() {
            return method + " " + path + (query == null ? "" : "?" + query);
        }
    }

    private final String       name;
    private final int          port;
    private final HttpServer    server;
    private final List<Rec>    received = Collections.synchronizedList(new ArrayList<>());

    public HttpMock(Vertx aVertx, String aName, int aPort, Responder aResponder) {
        this.name = aName;
        this.port = aPort;
        CountDownLatch started = new CountDownLatch(1);
        this.server = aVertx.createHttpServer()
                .requestHandler(request -> request.bodyHandler(buffer -> {
                    String body = buffer.toString();
                    received.add(new Rec(request.method().name(), request.path(), request.query(), body));
                    LOG.debug("[{}] {} {}", name, request.method(), request.uri());
                    try {
                        aResponder.respond(request, body);
                    } catch (RuntimeException e) {
                        LOG.error("[{}] responder failed", name, e);
                        if (!request.response().ended()) {
                            request.response().setStatusCode(500).end(e.toString());
                        }
                    }
                }))
                .listen(port, "127.0.0.1", ar -> started.countDown());
        awaitLatch(started);
    }

    public String name() {
        return name;
    }

    public int port() {
        return port;
    }

    public List<Rec> received() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }

    /** Polls the recorded requests until one matches {@code predicate} or the timeout elapses. */
    public boolean await(Predicate<Rec> aPredicate, long aTimeoutMs) {
        long deadline = System.currentTimeMillis() + aTimeoutMs;
        do {
            if (matches(aPredicate)) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return matches(aPredicate);
    }

    private boolean matches(Predicate<Rec> aPredicate) {
        synchronized (received) {
            for (Rec rec : received) {
                if (aPredicate.test(rec)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stop() {
        CountDownLatch done = new CountDownLatch(1);
        server.close(ar -> done.countDown());
        awaitLatch(done);
    }

    private static void awaitLatch(CountDownLatch aLatch) {
        try {
            aLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
