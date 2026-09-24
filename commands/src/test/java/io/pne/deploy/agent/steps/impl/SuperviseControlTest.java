package io.pne.deploy.agent.steps.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Controlling a service the way the {@code svc} program does: one letter into the supervisor's control channel.
 *
 * <p>The channel is a real named pipe here, because the whole point is how opening one behaves — it is what
 * tells a running supervisor apart from a missing one.
 */
public class SuperviseControlTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test(timeout = 20_000)
    public void sendsTheCommandLetterToAReadingSupervisor() throws Exception {
        Path control = namedPipe();
        BlockingQueue<Integer> read = new ArrayBlockingQueue<>(4);

        Thread supervisor = readingFrom(control, read);
        try {
            SuperviseControl.send(control, 'h');
            assertEquals("the letter a reload is asked for with", (Integer) (int) 'h',
                    read.poll(10, TimeUnit.SECONDS));
        } finally {
            supervisor.interrupt();
        }
    }

    @Test(timeout = 20_000)
    public void sendsTheOtherCommandLetterToo() throws Exception {
        Path control = namedPipe();
        BlockingQueue<Integer> read = new ArrayBlockingQueue<>(4);

        Thread supervisor = readingFrom(control, read);
        try {
            SuperviseControl.send(control, 't');
            assertEquals((Integer) (int) 't', read.poll(10, TimeUnit.SECONDS));
        } finally {
            supervisor.interrupt();
        }
    }

    @Test(timeout = 30_000)
    public void reportsThatNoSupervisorIsReading() throws Exception {
        Path control = namedPipe();

        // Nobody is reading the pipe, which is what a supervisor that is not running looks like.
        try {
            SuperviseControl.send(control, 'h');
            fail("expected the missing supervisor to be reported");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No supervisor is reading"));
        }
    }

    @Test(timeout = 20_000)
    public void reportsAMissingControlChannel() {
        Path control = folder.getRoot().toPath().resolve("no-such-service/supervise/control");

        try {
            SuperviseControl.send(control, 'h');
            fail("expected the missing channel to be reported");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("control"));
        }
    }

    @Test
    public void knowsWhereTheControlChannelLives() throws Exception {
        Path service = folder.getRoot().toPath();

        assertEquals(service.resolve("supervise/control"), SuperviseControl.controlFile(service));
    }

    private Path namedPipe() throws Exception {
        Path supervise = Files.createDirectories(folder.getRoot().toPath().resolve("supervise"));
        Path control   = supervise.resolve("control");

        Process mkfifo = new ProcessBuilder("mkfifo", control.toString()).start();
        assertEquals("mkfifo", 0, mkfifo.waitFor());
        return control;
    }

    /** Stands in for the supervisor: holds the reading end open and reports what arrives. */
    private static Thread readingFrom(Path aControl, BlockingQueue<Integer> aRead) throws Exception {
        Thread thread = new Thread(() -> {
            try (InputStream in = Files.newInputStream(aControl)) {
                int value;
                while ((value = in.read()) >= 0) {
                    aRead.add(value);
                }
            } catch (IOException e) {
                // the pipe went away with the test
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
