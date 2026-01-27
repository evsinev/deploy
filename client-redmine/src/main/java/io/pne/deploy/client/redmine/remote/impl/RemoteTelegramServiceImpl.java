package io.pne.deploy.client.redmine.remote.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.http.client.api.*;
import com.payneteasy.http.client.impl.HttpClientImpl;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteTelegramServiceImpl implements IRemoteTelegramService {
    private final IHttpClient client;
    private final Gson gson;
    private final HttpRequestParameters requestParameters = HttpRequestParameters.builder().timeouts(new HttpTimeouts(20_000, 20_000)).build();
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private final long telegramChatId;
    private final boolean telegramEnabled;
    private final String telegramUrl;

    public RemoteTelegramServiceImpl(IRedmineRemoteConfig aConfig) {
        telegramEnabled = aConfig.isTelegramEnabled();
        telegramChatId = aConfig.getTelegramChatId();
        telegramUrl = "https://api.telegram.org/bot" + aConfig.getTelegramToken();
        client = new HttpClientImpl();
        gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public void sendMessages(List<String> messages) {
        if (!telegramEnabled) {
            return;
        }
        for (String message: messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendMessage(String message) {
        if (!telegramEnabled) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", telegramChatId);
        body.put("text", message);
        body.put("parse_mode", "html");
        String requestBody = gson.toJson(body);
        String requestUrl = telegramUrl + "/sendMessage";
        HttpRequest request = HttpRequest.builder()
                .url(requestUrl)
                .headers(new HttpHeaders(Collections.singletonList(new HttpHeader(CONTENT_TYPE, APPLICATION_JSON))))
                .method(HttpMethod.POST)
                .body(requestBody.getBytes(UTF_8))
                .build();
        HttpResponse response;
        try {
            response = client.send(request, requestParameters);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send request to telegram", e);
        }
        if (response.getStatusCode() != 200) {
            throw new IllegalStateException(
                    "Unsuccessful sending message to Telegram. StatusCode=" + response.getStatusCode() + ", Message=" + message);
        }
    }
}
