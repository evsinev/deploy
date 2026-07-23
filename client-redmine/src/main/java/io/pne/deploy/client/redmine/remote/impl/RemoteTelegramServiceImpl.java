package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.telegram.bot.client.model.ParseMode;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;

import java.util.List;

public class RemoteTelegramServiceImpl implements IRemoteTelegramService {

    private final boolean        telegramEnabled;
    private final long           telegramChatId;
    private final TelegramClient telegram;

    public RemoteTelegramServiceImpl(IRedmineRemoteConfig aConfig) {
        telegramEnabled = aConfig.isTelegramEnabled();
        telegramChatId  = aConfig.getTelegramChatId();
        telegram        = new TelegramClient(aConfig.getTelegramToken());
    }

    @Override
    public void sendMessages(List<String> messages) {
        if (!telegramEnabled) {
            return;
        }
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!telegramEnabled) {
            return;
        }
        telegram.sendMessage(telegramChatId, message, ParseMode.HTML);
    }
}
