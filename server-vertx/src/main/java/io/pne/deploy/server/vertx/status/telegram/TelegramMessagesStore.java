package io.pne.deploy.server.vertx.status.telegram;

import io.pne.deploy.client.redmine.remote.impl.TelegramClient;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskId;

import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TelegramMessagesStore {

    /** Telegram ограничивает сообщение ~4096 символами; берём запас. */
    private static final int TG_SAFE_MAX = 4000;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ConcurrentMap<TaskId, TelegramMessageHolder> map = new ConcurrentHashMap<>();
    private final TelegramClient telegram;
    private final long           chatId;

    public TelegramMessagesStore(TelegramClient telegram, long chatId) {
        this.telegram = telegram;
        this.chatId   = chatId;
    }

    public void addTask(Task aTask, long aMessageId, String aText) {
        map.put(aTask.id, TelegramMessageHolder.builder()
                .messageId(aMessageId)
                .text(aText + "\n")
                .build());
    }

    public void updateTask(Task aTask, String aOriginalText) {
        TelegramMessageHolder holder = map.get(aTask.id);
        if (holder == null) {
            return;
        }

        String fixedText = aOriginalText
                .replace("./bin/", "")
                .replace(".sh", "");

        String line = "\n"
                + formatter.format(OffsetTime.now())
                + " "
                + fixedText;
        String candidate = holder.getText() + line;

        if (candidate.length() <= TG_SAFE_MAX) {
            telegram.editMessage(chatId, holder.getMessageId(), candidate, null);
            map.put(aTask.id, holder.toBuilder().text(candidate).build());
        } else {
            // Roll-over: сообщение подошло к лимиту — начинаем новое и продолжаем редактировать его.
            String start = "… (continued)" + line;
            if (start.length() > TG_SAFE_MAX) {
                start = start.substring(0, TG_SAFE_MAX);
            }
            long newMessageId = telegram.sendMessage(chatId, start, null);
            map.put(aTask.id, holder.toBuilder().messageId(newMessageId).text(start).build());
        }
    }

    public void removeTask(Task aTask) {
        map.remove(aTask.id);
    }
}
