package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.TelegramApiMock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvalidCommandTest {

    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);

    @Test
    public void execute() {
        final ExecutableCommand command = new InvalidCommand(1L, "test error");
        command.execute(null, telegramService);

        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        1L,
                        null,
                        "Invalid command: test error"
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void estimateWriteCost() {
        final ExecutableCommand command = new InvalidCommand(1L, "test error");

        assertEquals(0, command.estimateWriteCost(null));
    }

}
