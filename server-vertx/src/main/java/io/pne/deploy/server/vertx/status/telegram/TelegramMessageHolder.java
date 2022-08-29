package io.pne.deploy.server.vertx.status.telegram;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Builder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
public class TelegramMessageHolder {
    int chatId;
    int messageId;
    String text;
}
