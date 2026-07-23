package io.pne.deploy.server.vertx.status.telegram;

import io.pne.deploy.client.redmine.remote.impl.TelegramClient;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskId;
import io.pne.deploy.server.api.task.TaskParameters;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TelegramMessagesStoreTest {

    private static final long CHAT_ID    = 42L;
    private static final long FIRST_MSG  = 100L;
    private static final long ROLLED_MSG = 200L;

    private final TelegramClient       telegram = mock(TelegramClient.class);
    private final TelegramMessagesStore store   = new TelegramMessagesStore(telegram, CHAT_ID);

    @Test
    public void rollsOverToNewMessageWhenTextExceedsLimit() {
        // any roll-over send returns a distinct message id
        when(telegram.sendMessage(anyLong(), anyString(), any())).thenReturn(ROLLED_MSG);

        Task task = new Task(TaskId.generateTaskId(), new TaskParameters(),
                Collections.emptyList(), "deploy something", 0);
        store.addTask(task, FIRST_MSG, "start");

        // push enough long lines to blow past TG_SAFE_MAX (4000)
        String bigLine = repeat("x", 1000);
        for (int i = 0; i < 8; i++) {
            store.updateTask(task, bigLine);
        }

        // before overflow: the original message was edited
        verify(telegram, atLeastOnce()).editMessage(eq(CHAT_ID), eq(FIRST_MSG), anyString(), any());
        // at overflow: a brand-new message was sent (roll-over)
        verify(telegram, atLeastOnce()).sendMessage(eq(CHAT_ID), anyString(), any());
        // after overflow: edits target the new (rolled-over) message
        verify(telegram, atLeastOnce()).editMessage(eq(CHAT_ID), eq(ROLLED_MSG), anyString(), any());
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
