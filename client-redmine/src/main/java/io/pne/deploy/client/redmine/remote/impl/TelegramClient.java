package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Единственная точка работы с telegram-bot-client. Все вызовы идут через один поток-воркер с
 * ограничением темпа (не чаще одного обращения в {@code minIntervalMs}) и повтором на 429 по
 * {@code retry_after}. Частые правки одного и того же сообщения коалесятся до последнего текста.
 */
public class TelegramClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramClient.class);

    private static final long DEFAULT_MIN_INTERVAL_MS = 1100;
    private static final int  MAX_RETRIES             = 5;

    private final ITelegramService telegram;
    private final long             minIntervalMs;

    private final BlockingQueue<Runnable> queue        = new LinkedBlockingQueue<>();
    private final Map<Long, PendingEdit>  pendingEdits = new HashMap<>(); // guarded by itself

    private volatile long lastCallAt = 0;

    public TelegramClient(String aToken) {
        this(new TelegramServiceImpl(new TelegramHttpClientImpl(aToken)), DEFAULT_MIN_INTERVAL_MS);
    }

    public TelegramClient(ITelegramService aTelegram, long aMinIntervalMs) {
        this.telegram      = aTelegram;
        this.minIntervalMs = aMinIntervalMs;
        Thread worker = new Thread(this::runWorker, "telegram-sender");
        worker.setDaemon(true);
        worker.start();
    }

    /** Блокирующая отправка нового сообщения; возвращает message_id. */
    public long sendMessage(long aChatId, String aText, ParseMode aParseMode) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                future.complete(callWithRetry(() -> telegram.sendMessage(TelegramMessageRequest.builder()
                                .chatId(aChatId)
                                .text(aText)
                                .parseMode(aParseMode)
                                .build())
                        .getResult()
                        .getMessageId()));
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending Telegram message", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    /** Асинхронная правка с коалесингом: сохраняем последний текст и планируем один вызов на messageId. */
    public void editMessage(long aChatId, long aMessageId, String aText, ParseMode aParseMode) {
        boolean schedule;
        synchronized (pendingEdits) {
            schedule = !pendingEdits.containsKey(aMessageId);
            pendingEdits.put(aMessageId, new PendingEdit(aChatId, aText, aParseMode));
        }
        if (schedule) {
            queue.add(() -> flushEdit(aMessageId));
        }
    }

    private void flushEdit(long aMessageId) {
        PendingEdit edit;
        synchronized (pendingEdits) {
            edit = pendingEdits.remove(aMessageId);
        }
        if (edit == null) {
            return;
        }
        callWithRetry(() -> {
            telegram.editMessageText(EditMessageTextRequest.builder()
                    .chatId(edit.chatId)
                    .messageId(aMessageId)
                    .text(edit.text)
                    .parseMode(edit.parseMode)
                    .build());
            return null;
        });
    }

    private void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            Runnable job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                job.run();
            } catch (Exception e) {
                LOG.error("Telegram job failed", e);
            }
        }
    }

    private <T> T callWithRetry(Supplier<T> aCall) {
        for (int attempt = 1; ; attempt++) {
            awaitRateLimit();
            try {
                return aCall.get();
            } catch (TelegramCommandException e) {
                Integer code = e.getErrorCode();
                if (code != null && code == 429 && attempt <= MAX_RETRIES) {
                    long waitMs = e.getRetryAfter() != null ? e.getRetryAfter() * 1000L : minIntervalMs;
                    LOG.warn("Telegram 429, retry #{} after {}ms", attempt, waitMs);
                    sleep(waitMs);
                    continue;
                }
                throw e;
            } finally {
                lastCallAt = System.currentTimeMillis();
            }
        }
    }

    private void awaitRateLimit() {
        sleep(lastCallAt + minIntervalMs - System.currentTimeMillis());
    }

    private static void sleep(long aMs) {
        if (aMs <= 0) {
            return;
        }
        try {
            Thread.sleep(aMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class PendingEdit {
        final long      chatId;
        final String    text;
        final ParseMode parseMode;

        PendingEdit(long aChatId, String aText, ParseMode aParseMode) {
            this.chatId    = aChatId;
            this.text      = aText;
            this.parseMode = aParseMode;
        }
    }
}
