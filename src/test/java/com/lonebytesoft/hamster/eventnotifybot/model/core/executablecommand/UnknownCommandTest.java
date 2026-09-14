package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import com.lonebytesoft.hamster.eventnotifybot.test.TelegramApiMock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnknownCommandTest {

    private final TelegramApiMock telegramApi = new TelegramApiMock();
    private final TelegramService telegramService = new TelegramService(telegramApi);

    @Test
    public void execute() {
        final ExecutableCommand command = new UnknownCommand(1L, "test");
        command.execute(null, telegramService);

        assertEquals(
                List.of(new TelegramApiMock.SentMessage(
                        1L,
                        null,
                        "Unrecognized command: <b>test</b>"
                )),
                telegramApi.getSentMessages()
        );
    }

    @Test
    public void estimateWriteCost() {
        final ExecutableCommand command = new UnknownCommand(1L, "test");

        assertEquals(0, command.estimateWriteCost(null));
    }

}
