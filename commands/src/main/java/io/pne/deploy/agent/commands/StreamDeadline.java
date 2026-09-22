package io.pne.deploy.agent.commands;

import java.io.Closeable;

/**
 * Closes a stream once a deadline passes.
 *
 * <p>A read timeout only covers the wait for the next byte, so a source that dribbles out one byte at a time, or
 * that sends headers and then stops, can hold a transfer open indefinitely without ever tripping it. Checking the
 * clock between reads does not help either: the thread is blocked inside the read. Closing the stream from another
 * thread is what actually unblocks it.
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
