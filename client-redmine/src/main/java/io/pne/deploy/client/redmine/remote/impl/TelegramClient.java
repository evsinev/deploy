package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.telegram.bot.client.ITelegramService;
import com.payneteasy.telegram.bot.client.http.TelegramHttpClientImpl;
import com.payneteasy.telegram.bot.client.impl.TelegramServiceImpl;
import com.payneteasy.telegram.bot.client.messages.EditMessageTextRequest;
import com.payneteasy.telegram.bot.client.messages.TelegramMessageRequest;
import com.payneteasy.telegram.bot.client.model.ParseMode;

/**
 * Единственная точка работы с библиотекой telegram-bot-client в проекте: используется и
 * client-redmine (diff-уведомления, HTML), и server-vertx (live-статус, плоский текст).
 * {@code parseMode} может быть null (плоский текст).
 */
public class TelegramClient {

    private final ITelegramService telegram;

    public TelegramClient(String aToken) {
        this.telegram = new TelegramServiceImpl(new TelegramHttpClientImpl(aToken));
    }

    /** @return message_id отправленного сообщения */
    public long sendMessage(long aChatId, String aText, ParseMode aParseMode) {
        return telegram.sendMessage(TelegramMessageRequest.builder()
                        .chatId(aChatId)
                        .text(aText)
                        .parseMode(aParseMode)
                        .build())
                .getResult()
                .getMessageId();
    }

    public void editMessage(long aChatId, long aMessageId, String aText, ParseMode aParseMode) {
        telegram.editMessageText(EditMessageTextRequest.builder()
                .chatId(aChatId)
                .messageId(aMessageId)
                .text(aText)
                .parseMode(aParseMode)
                .build());
    }
}
