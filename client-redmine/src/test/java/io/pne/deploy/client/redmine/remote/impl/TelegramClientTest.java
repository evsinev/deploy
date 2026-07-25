package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.model.Message;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelegramClientTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ITelegramService service = mock(ITelegramService.class);
    private TelegramClient         client;

    @Before
    public void setUp() throws Exception {
        client = new TelegramClient(service, 0L, tmp.newFolder("main")); // no pacing delay in tests
    }

    @Test
    public void sendMessageReturnsMessageId() {
        TelegramMessage sent = message(555L);
        when(service.sendMessage(any())).thenReturn(sent);
        assertEquals(555L, client.sendMessage(1L, "hi", ParseMode.HTML));
    }

    @Test
    public void retriesOnTooManyRequests() {
        TelegramMessage sent = message(7L);
        when(service.sendMessage(any()))
                .thenThrow(new TelegramCommandException("Too Many Requests", "1", 429, 0))
                .thenReturn(sent);

        assertEquals(7L, client.sendMessage(1L, "hi", null));
        verify(service, times(2)).sendMessage(any());
    }

    @Test
    public void recordsSendLatencyOnSuccess() throws Exception {
        AtomicLong recorded = new AtomicLong(-1);
        TelegramClient timed = new TelegramClient(service, 0L, tmp.newFolder("latency"), recorded::set);

        TelegramMessage sent = message(1L);
        when(service.sendMessage(any())).thenReturn(sent);

        timed.sendMessage(1L, "hi", ParseMode.HTML);
        assertTrue("latency should be recorded and positive", recorded.get() > 0);
    }

    @Test
    public void coalescesRapidEditsOfSameMessage() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger  calls   = new AtomicInteger();
        when(service.editMessageText(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                release.await();
            }
            return null;
        });

        client.editMessage(1L, 10L, "t0", null);
        assertTrue(entered.await(2, SECONDS));

        for (int i = 1; i <= 20; i++) {
            client.editMessage(1L, 10L, "t" + i, null);
        }
        release.countDown();

        ArgumentCaptor<EditMessageTextRequest> captor = ArgumentCaptor.forClass(EditMessageTextRequest.class);
        verify(service, timeout(2000).times(2)).editMessageText(captor.capture());
        verify(service, after(300).times(2)).editMessageText(any());
        assertEquals("t20", captor.getAllValues().get(1).getText());
    }

    @Test
    public void resendsPersistedOperationsOnStartup() throws Exception {
        File dir = tmp.newFolder("replay");
        // a SEND that was persisted but not confirmed sent before a restart
        Files.write(new File(dir, "00000000000000000001.json").toPath(),
                "{\"kind\":\"SEND\",\"chatId\":42,\"text\":\"pending\",\"parseMode\":\"HTML\"}".getBytes(UTF_8));

        TelegramMessage sent = message(999L);
        when(service.sendMessage(any())).thenReturn(sent);

        // constructing the client replays the spool and resends
        new TelegramClient(service, 0L, dir);

        verify(service, timeout(2000)).sendMessage(any());
        // successfully sent -> spool file removed
        Thread.sleep(200);
        assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".json")).length);
    }

    @Test
    public void deadLettersSendAfterMaxAttempts() throws Exception {
        File dir = tmp.newFolder("dead-letter");
        // always 429 with retry_after=0 -> retries are immediate (no real backoff), fast test
        when(service.sendMessage(any())).thenThrow(new TelegramCommandException("Too Many Requests", "1", 429, 0));

        TelegramClient failing = new TelegramClient(service, 0L, dir);
        try {
            failing.sendMessage(1L, "boom", null);
            fail("expected failure after max attempts");
        } catch (RuntimeException expected) {
            // ok — gave up after MAX_ATTEMPTS
        }

        verify(service, times(10)).sendMessage(any()); // Backoff.MAX_ATTEMPTS
        assertEquals(0, dir.listFiles((d, n) -> n.endsWith(".json")).length);       // removed from active spool
        assertEquals(1, new File(dir, "dead").listFiles((d, n) -> n.endsWith(".json")).length); // moved to dead-letter
    }

    @Test
    public void closeAbortsInFlightRetryPromptly() throws Exception {
        File dir = tmp.newFolder("close");
        // a generic failure (not a 429) would otherwise back off exponentially for minutes
        when(service.sendMessage(any())).thenThrow(new RuntimeException("connection refused"));

        TelegramClient failing = new TelegramClient(service, 0L, dir);
        AtomicReference<RuntimeException> thrown = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                failing.sendMessage(1L, "boom", null);
            } catch (RuntimeException e) {
                thrown.set(e);
            }
        }, "test-caller");
        caller.start();

        // the worker has failed once and is now in the (long) backoff sleep
        verify(service, timeout(2000).atLeastOnce()).sendMessage(any());

        long start = System.currentTimeMillis();
        failing.close();          // interrupts the worker -> aborts the backoff
        caller.join(3000);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse("sendMessage caller must return after close()", caller.isAlive());
        assertTrue("close() should abort the retry storm promptly, took " + elapsed + "ms", elapsed < 2000);
        assertTrue("caller should observe a failure", thrown.get() != null);

        // on shutdown the op is kept for replay, not dead-lettered
        File[] dead = new File(dir, "dead").listFiles((d, n) -> n.endsWith(".json"));
        assertTrue("op must not be dead-lettered on shutdown", dead == null || dead.length == 0);
        assertEquals("op stays in the active spool for replay", 1,
                dir.listFiles((d, n) -> n.endsWith(".json")).length);
    }

    private static TelegramMessage message(long aId) {
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(aId);
        TelegramMessage telegramMessage = mock(TelegramMessage.class);
        when(telegramMessage.getResult()).thenReturn(message);
        return telegramMessage;
    }
}
