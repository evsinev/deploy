package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.TelegramCommandException;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.ParseMode;
import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * Единственная точка работы с telegram-bot-client. Все вызовы идут через один поток-воркер с
 * ограничением темпа и повтором на 429 по {@code retry_after}. Частые правки одного сообщения коалесятся.
 * Операции персистятся на диск (spool) и переотправляются после рестарта (at-least-once).
 */
public class TelegramClient {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramClient.class);

    private static final long DEFAULT_MIN_INTERVAL_MS = 1100;
    private static final int  MAX_RETRIES             = 5;

    private final ITelegramService telegram;
    private final long             minIntervalMs;
    private final PersistentSpool  spool;
    private final Gson             gson = new GsonBuilder().disableHtmlEscaping().create();

    private final BlockingQueue<Runnable> queue        = new LinkedBlockingQueue<>();
    private final Map<Long, PendingEdit>  pendingEdits = new HashMap<>(); // guarded by itself

    private volatile long lastCallAt = 0;

    public TelegramClient(String aToken, File aSpoolDir) {
        this(new TelegramServiceImpl(new TelegramHttpClientImpl(aToken)), DEFAULT_MIN_INTERVAL_MS, aSpoolDir);
    }

    public TelegramClient(ITelegramService aTelegram, long aMinIntervalMs, File aSpoolDir) {
        this.telegram      = aTelegram;
        this.minIntervalMs = aMinIntervalMs;
        this.spool         = new PersistentSpool(aSpoolDir);

        Thread worker = new Thread(this::runWorker, "telegram-sender");
        worker.setDaemon(true);
        worker.start();

        replay();
    }

    /** Блокирующая отправка нового сообщения; возвращает message_id. */
    public long sendMessage(long aChatId, String aText, ParseMode aParseMode) {
        TelegramOp op   = TelegramOp.send(aChatId, aText, aParseMode);
        String     file = spool.append(gson.toJson(op));
        CompletableFuture<Long> future = new CompletableFuture<>();
        queue.add(() -> runSend(file, op, future));
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

    /** Асинхронная правка с коалесингом: сохраняем последний текст (overwrite в spool) и планируем один вызов. */
    public void editMessage(long aChatId, long aMessageId, String aText, ParseMode aParseMode) {
        TelegramOp op   = TelegramOp.edit(aChatId, aMessageId, aText, aParseMode);
        String     file = spool.put(editKey(aChatId, aMessageId), gson.toJson(op));
        boolean    schedule;
        synchronized (pendingEdits) {
            schedule = !pendingEdits.containsKey(aMessageId);
            pendingEdits.put(aMessageId, new PendingEdit(aChatId, aText, aParseMode, file));
        }
        if (schedule) {
            queue.add(() -> flushEdit(aMessageId));
        }
    }

    private void runSend(String aFile, TelegramOp aOp, CompletableFuture<Long> aFuture) {
        try {
            long id = callWithRetry(() -> telegram.sendMessage(TelegramMessageRequest.builder()
                            .chatId(aOp.chatId)
                            .text(aOp.text)
                            .parseMode(parseMode(aOp.parseMode))
                            .build())
                    .getResult()
                    .getMessageId());
            spool.remove(aFile);
            if (aFuture != null) {
                aFuture.complete(id);
            }
        } catch (RuntimeException e) {
            // keep the spool file — the message will be retried on the next restart
            if (aFuture != null) {
                aFuture.completeExceptionally(e);
            } else {
                LOG.error("telegram send failed (kept for retry on restart)", e);
            }
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
        try {
            callWithRetry(() -> {
                telegram.editMessageText(EditMessageTextRequest.builder()
                        .chatId(edit.chatId)
                        .messageId(aMessageId)
                        .text(edit.text)
                        .parseMode(edit.parseMode)
                        .build());
                return null;
            });
            spool.remove(edit.fileName);
        } catch (RuntimeException e) {
            LOG.error("telegram edit failed (kept for retry on restart)", e);
        }
    }

    /** Re-enqueue operations that were persisted but not confirmed sent before a restart. */
    private void replay() {
        for (PersistentSpool.Stored stored : spool.loadAll()) {
            TelegramOp op = gson.fromJson(stored.getJson(), TelegramOp.class);
            if (TelegramOp.EDIT.equals(op.kind) && op.messageId != null) {
                synchronized (pendingEdits) {
                    pendingEdits.put(op.messageId, new PendingEdit(op.chatId, op.text, parseMode(op.parseMode), stored.getFileName()));
                }
                long messageId = op.messageId;
                queue.add(() -> flushEdit(messageId));
            } else {
                queue.add(() -> runSend(stored.getFileName(), op, null));
            }
        }
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

    private static ParseMode parseMode(String aName) {
        return aName == null ? null : ParseMode.valueOf(aName);
    }

    private static String editKey(long aChatId, long aMessageId) {
        return "edit-" + aChatId + "-" + aMessageId;
    }

    private static final class PendingEdit {
        final long      chatId;
        final String    text;
        final ParseMode parseMode;
        final String    fileName;

        PendingEdit(long aChatId, String aText, ParseMode aParseMode, String aFileName) {
            this.chatId    = aChatId;
            this.text      = aText;
            this.parseMode = aParseMode;
            this.fileName  = aFileName;
        }
    }

    /** Persisted, replayable Telegram operation. */
    private static final class TelegramOp {
        static final String SEND = "SEND";
        static final String EDIT = "EDIT";

        String kind;
        long   chatId;
        Long   messageId;
        String text;
        String parseMode; // ParseMode name or null

        TelegramOp() {
            // for gson
        }

        static TelegramOp send(long aChatId, String aText, ParseMode aParseMode) {
            TelegramOp op = new TelegramOp();
            op.kind      = SEND;
            op.chatId    = aChatId;
            op.text      = aText;
            op.parseMode = aParseMode == null ? null : aParseMode.name();
            return op;
        }

        static TelegramOp edit(long aChatId, long aMessageId, String aText, ParseMode aParseMode) {
            TelegramOp op = new TelegramOp();
            op.kind      = EDIT;
            op.chatId    = aChatId;
            op.messageId = aMessageId;
            op.text      = aText;
            op.parseMode = aParseMode == null ? null : aParseMode.name();
            return op;
        }
    }
}
