package io.pne.deploy.agent.commands;

import java.io.Closeable;

/**
 * Closes a stream once a deadline passes.
 *
 * <p>A read timeout only covers the wait for the next byte, so a source that dribbles out one byte at a time, or
 * that sends headers and then stops, can hold a transfer open indefinitely without ever tripping it. Checking the
 * clock between reads does not help either: the thread is blocked inside the read. Closing the stream from another
 * thread is what unblocks it.
 *
 * <p>How promptly it unblocks depends on the stream. The body of a {@code java.net.http} response is released at
 * once; a connection kept alive by the older {@code HttpURLConnection} may only be released when the blocked read
 * itself returns, so there the read timeout and a limit on how much will be read remain the real defence.
 *
 * <p>Cancelling asks the watchdog to stand down. It is a courtesy, not a barrier: if the deadline has already
 * passed the stream may still be closed, which is harmless once the caller has what it came for.
 */
public final class StreamDeadline implements AutoCloseable {

    private final Thread watchdog;

    private StreamDeadline(Thread aWatchdog) {
        watchdog = aWatchdog;
    }

    public static StreamDeadline closeAt(Closeable aTarget, long aDeadlineMillis) {
        Thread watchdog = new Thread(() -> {
            try {
                long remaining = aDeadlineMillis - System.currentTimeMillis();
                if (remaining > 0) {
                    Thread.sleep(remaining);
                }
                aTarget.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // The stream is being abandoned anyway; there is nothing useful to do with a failure to close it.
            }
        });
        watchdog.setName("transfer-deadline");
        watchdog.setDaemon(true);
        watchdog.start();
        return new StreamDeadline(watchdog);
    }

    @Override
    public void close() {
        watchdog.interrupt();
    }
}
