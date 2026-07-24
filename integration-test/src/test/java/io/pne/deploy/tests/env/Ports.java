package io.pne.deploy.tests.env;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/** Picks an ephemeral free TCP port. There is a tiny race between close and reuse, acceptable for a local harness. */
final class Ports {

    private Ports() {
    }

    static int free() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot allocate a free port", e);
        }
    }
}
