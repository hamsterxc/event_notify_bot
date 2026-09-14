package com.lonebytesoft.hamster.eventnotifybot.service.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.InvalidCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.UnknownCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandParsingServiceTest {

    private final CommandParsingService commandParsingService = new CommandParsingService();

    @Test
    public void parseMessage_emptyCommand_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        null
                ))
        );
    }

    @Test
    public void parseMessage_nonCommand_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "hello world"
                ))
        );
    }

    @Test
    public void parseMessage_nonAlphanumericCommand_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/hello! world"
                ))
        );
    }

    @Test
    public void parseMessage_anotherBot_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/hello@world"
                ))
        );
    }

    @Test
    public void parseMessage_missingChat_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        null,
                        "group",
                        "/test"
                ))
        );
    }

    @Test
    public void parseMessage_unknownCommandBroadcast_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/random"
                ))
        );
    }

    @Test
    public void parseMessage_unknownCommandDirectMention_unknownCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "unknown",
                        List.of("random")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/random@hamster_event_bot"
                ))
        );
    }

    @Test
    public void parseMessage_unknownCommandPrivateChat_unknownCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "unknown",
                        List.of("random")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "private",
                        "/random"
                ))
        );
    }

    @Test
    public void parseMessage_tooLongCommand_invalidCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "invalid",
                        List.of("Command too long")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/test " + IntStream.range(0, 500).mapToObj(_ -> "-").collect(Collectors.joining())
                ))
        );
    }

    @Test
    public void parseMessage_validCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "test",
                        List.of("First", "Second")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/test First Second"
                ))
        );
    }

    @Test
    public void parseCommand_unknown() {
        assertEquals(
                Optional.of(new UnknownCommand(
                        2L,
                        "random"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "unknown",
                        List.of("random")
                ))
        );
    }

    @Test
    public void parseCommand_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "Command too long"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "invalid",
                        List.of("Command too long")
                ))
        );
    }

    private static Message message(
            final Long time,
            final Long chatId,
            final String chatType,
            final String text
    ) {
        return new Message(
                null,
                time,
                new Chat(chatId, chatType),
                text
        );
    }

}
