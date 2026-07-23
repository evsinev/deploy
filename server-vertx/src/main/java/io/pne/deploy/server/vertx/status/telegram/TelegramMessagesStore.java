package io.pne.deploy.server.vertx.status.telegram;

import io.pne.deploy.client.redmine.remote.impl.TelegramClient;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskId;

import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TelegramMessagesStore {

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

        String candidate = holder.getText()
                + "\n"
                + formatter.format(OffsetTime.now())
                + " "
                + fixedText;

        telegram.editMessage(chatId, holder.getMessageId(), candidate, null);
        map.put(aTask.id, holder.toBuilder().text(candidate).build());
    }

    public void removeTask(Task aTask) {
        map.remove(aTask.id);
    }
}
