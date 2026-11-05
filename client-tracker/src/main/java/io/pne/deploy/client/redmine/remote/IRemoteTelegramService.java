package io.pne.deploy.client.redmine.remote;

import java.util.List;

public interface IRemoteTelegramService {
    void sendMessage(String message);
    void sendMessages(List<String> messages);
}
