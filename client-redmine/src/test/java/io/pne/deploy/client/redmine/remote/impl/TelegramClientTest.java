package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.model.Message;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelegramClientTest {

    private final ITelegramService service = mock(ITelegramService.class);
    private final TelegramClient   client  = new TelegramClient(service, 0L); // no pacing delay in tests

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
    public void coalescesRapidEditsOfSameMessage() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger  calls   = new AtomicInteger();
        when(service.editMessageText(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                entered.countDown();
                release.await(); // hold the worker inside the first edit
            }
            return null;
        });

        client.editMessage(1L, 10L, "t0", null);       // schedules the first flush
        assertTrue(entered.await(2, SECONDS));           // worker is now blocked in the first editMessageText

        for (int i = 1; i <= 20; i++) {
            client.editMessage(1L, 10L, "t" + i, null);  // coalesce into a single follow-up flush
        }
        release.countDown();

        // exactly 2 calls: the in-flight one + one coalesced carrying the latest text
        ArgumentCaptor<EditMessageTextRequest> captor = ArgumentCaptor.forClass(EditMessageTextRequest.class);
        verify(service, timeout(2000).times(2)).editMessageText(captor.capture());
        verify(service, after(300).times(2)).editMessageText(any());
        assertEquals("t20", captor.getAllValues().get(1).getText());
    }

    private static TelegramMessage message(long aId) {
        Message message = mock(Message.class);
        when(message.getMessageId()).thenReturn(aId);
        TelegramMessage telegramMessage = mock(TelegramMessage.class);
        when(telegramMessage.getResult()).thenReturn(message);
        return telegramMessage;
    }
}
