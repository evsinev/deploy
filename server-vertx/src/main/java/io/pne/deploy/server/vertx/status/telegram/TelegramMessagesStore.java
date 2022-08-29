package io.pne.deploy.server.vertx.status.telegram;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.model.Message;
import com.payneteasy.telegram.bot.client.model.TelegramMessage;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskId;

import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TelegramMessagesStore {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ConcurrentMap<TaskId, TelegramMessageHolder> map = new ConcurrentHashMap<>();
    private final ITelegramService                             telegram;

    public TelegramMessagesStore(ITelegramService telegram) {
        this.telegram = telegram;
    }

    public void addTask(Task aTask, TelegramMessage aTelegramMessage, String aText) {
        Message message = aTelegramMessage.getResult();
        map.put(aTask.id, TelegramMessageHolder.builder()
                .messageId  ( message.getMessageId()    )
                .chatId     ( message.getChat().getId() )
                .text       ( aText + "\n"              )
                .build()
        );
    }

    public void updateTask(Task aTask, String aOriginalText) {
        String fixedText = aOriginalText
                .replace("./bin/", "")
                .replace(".sh", "")
        ;
        
        TelegramMessageHolder holder = map.computeIfPresent(aTask.id, (taskId, old) -> old.toBuilder()
                .text(old.getText()
                        + "\n"
                        + formatter.format(OffsetTime.now())
                        +" "
                        + fixedText)
                .build());

        if(holder == null) {
            return;
        }

        telegram.editMessageText(EditMessageTextRequest.builder()
                .chatId(holder.getChatId())
                .messageId(holder.getMessageId())
                .text(holder.getText())
                .build()
        );

    }

    public void removeTask(Task aTask) {
        map.remove(aTask.id);
    }
}
