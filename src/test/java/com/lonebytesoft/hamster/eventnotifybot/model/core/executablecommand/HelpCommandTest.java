package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.TelegramApiMock;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HelpCommandTest {

    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);

    @Test
    public void execute() {
        final ExecutableCommand command = new HelpCommand(1L);
        command.execute(null, telegramService);

        final Collection<TelegramApiMock.SentMessage> sentMessages = telegramApi.getSentMessages();
        assertEquals(1, sentMessages.size());

        final TelegramApiMock.SentMessage sentMessage = sentMessages.iterator().next();
        assertEquals(1L, sentMessage.chatId());
        assertNull(sentMessage.imageUrl());
        assertTrue(sentMessage.text().contains("/help") && sentMessage.text().contains("/start"));
    }

    @Test
    public void estimateWriteCost() {
        final ExecutableCommand command = new HelpCommand(1L);

        assertEquals(0, command.estimateWriteCost(null));
    }

}
