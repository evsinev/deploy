package io.pne.deploy.agent.steps.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asks a supervisor to act on a service by writing to its control channel, the way the {@code svc} program does.
 *
 * <p>The control channel is a named pipe the supervisor reads commands from; one letter is one command, and it is
 * the supervisor that then signals the service. Writing that letter directly is the whole of what the program
 * does, so there is no reason to start a process for it - and not starting one keeps the agent from looking, to
 * anything watching the host, like it is launching programs of its own.
 *
 * <p>A supervisor that is running holds the reading end of the pipe open, so the write happens at once. A
 * supervisor that is not running leaves nobody reading, and opening the pipe blocks - which is how its absence is
 * noticed here. The blocked open is abandoned after a moment, and the command is not sent afterwards: a command
 * arriving whenever the supervisor happens to start again would be worse than none at all.
 */
final class SuperviseControl {

    /** A running supervisor answers at once; this only bounds the wait when there is none. */
    private static final long OPEN_TIMEOUT_MILLIS = 5_000;

    private SuperviseControl() {
    }

    static Path controlFile(Path aServiceDirectory) {
        return aServiceDirectory.resolve("supervise").resolve("control");
    }

    /**
     * Writes one command letter.
     *
     * @throws IOException the channel is missing, unwritable, or no supervisor is reading it
     */
    static void send(Path aControlFile, char aCommand) throws IOException {
        AtomicBoolean            claimed = new AtomicBoolean(false);
        AtomicReference<IOException> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            // Opening a pipe for writing waits until something is reading it.
            try (FileChannel channel = FileChannel.open(aControlFile,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                if (!claimed.compareAndSet(false, true)) {
                    return;         // given up on already; sending now would be a command out of its time
                }
                channel.write(ByteBuffer.wrap(new byte[]{(byte) aCommand}));
            } catch (IOException e) {
                claimed.compareAndSet(false, true);
                failure.set(e);
            }
        });
        writer.setName("supervise-control");
        writer.setDaemon(true);
        writer.start();

        try {
            writer.join(OPEN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            claimed.compareAndSet(false, true);
            throw new IOException("Interrupted while controlling " + aControlFile, e);
        }

        if (claimed.compareAndSet(false, true)) {
            throw new IOException("No supervisor is reading " + aControlFile
                    + "; it is not running, so the service cannot be controlled");
        }

        IOException thrown = failure.get();
        if (thrown != null) {
            throw thrown;
        }
    }

    /** Whether the service directory looks like one a supervisor manages. */
    static boolean looksSupervised(Path aServiceDirectory) {
        return Files.exists(controlFile(aServiceDirectory));
    }
}
