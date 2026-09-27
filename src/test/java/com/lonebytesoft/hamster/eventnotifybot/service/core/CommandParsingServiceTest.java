package com.lonebytesoft.hamster.eventnotifybot.service.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.HelpCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.InvalidCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.ShowCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.StatusCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.UnknownCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.test.ProviderMock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandParsingServiceTest {

    private final Provider provider = new ProviderMock("provider", null, null, null);
    private final CommandParsingService commandParsingService = new CommandParsingService(List.of(provider));

    @Test
    public void parseMessage_emptyMessage_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "private",
                        null
                ))
        );
    }

    @Test
    public void parseMessage_emptyCommand_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "private",
                        "/"
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
                        "private",
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
                        "private",
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
                        "private",
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
                        "/show"
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
    public void parseMessage_unknownCommandBroadcastDirectMention_unknownCommand() {
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
    public void parseMessage_recognizedUnknownCommandBroadcast_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/unknown"
                ))
        );
    }

    @Test
    public void parseMessage_recognizedUnknownCommandBroadcastDirectMention_unknownCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "unknown",
                        List.of("unknown")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/unknown@hamster_event_bot"
                ))
        );
    }

    @Test
    public void parseMessage_recognizedUnknownCommandPrivateChat_unknownCommand() {
        assertEquals(
                Optional.of(new Command(
                        null,
                        2L,
                        1L,
                        "unknown",
                        List.of("unknown")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "private",
                        "/unknown"
                ))
        );
    }

    @Test
    public void parseMessage_tooLongCommandBroadcast_ignored() {
        assertEquals(
                Optional.empty(),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/" + IntStream.range(0, 500).mapToObj(_ -> "a").collect(Collectors.joining())
                ))
        );
    }

    @Test
    public void parseMessage_tooLongCommandPrivateChat_invalidCommand() {
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
                        "private",
                        "/" + IntStream.range(0, 500).mapToObj(_ -> "a").collect(Collectors.joining())
                ))
        );
    }

    @Test
    public void parseMessage_tooLongParametersBroadcast_invalidCommand() {
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
                        "/show " + IntStream.range(0, 500).mapToObj(_ -> "-").collect(Collectors.joining())
                ))
        );
    }

    @Test
    public void parseMessage_tooLongParametersPrivateChat_invalidCommand() {
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
                        "private",
                        "/show " + IntStream.range(0, 500).mapToObj(_ -> "-").collect(Collectors.joining())
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
                        "show",
                        List.of("First")
                )),
                commandParsingService.parseMessage(message(
                        1L,
                        2L,
                        "group",
                        "/show First"
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

    @Test
    public void parseCommand_start() {
        assertEquals(
                Optional.of(new HelpCommand(
                        2L
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "start",
                        List.of()
                ))
        );
    }

    @Test
    public void parseCommand_startWithParameters_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "No parameters expected for command start"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "start",
                        List.of("foo", "bar")
                ))
        );
    }

    @Test
    public void parseCommand_help() {
        assertEquals(
                Optional.of(new HelpCommand(
                        2L
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "help",
                        List.of()
                ))
        );
    }

    @Test
    public void parseCommand_helpWithParameters_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "No parameters expected for command help"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "help",
                        List.of("foo", "bar")
                ))
        );
    }

    @Test
    public void parseCommand_status() {
        assertEquals(
                Optional.of(new StatusCommand(
                        2L
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "status",
                        List.of()
                ))
        );
    }

    @Test
    public void parseCommand_statusWithParameters_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "No parameters expected for command status"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "status",
                        List.of("foo", "bar")
                ))
        );
    }

    @Test
    public void parseCommand_showNoParameters_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "Exactly 1 parameter expected for command show"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "show",
                        List.of()
                ))
        );
    }

    @Test
    public void parseCommand_showOneParameterKnownProvider() {
        assertEquals(
                Optional.of(new ShowCommand(
                        2L,
                        provider
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "show",
                        List.of("provider")
                ))
        );
    }

    @Test
    public void parseCommand_showOneParameterUnknownProvider_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "Unknown provider foo"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "show",
                        List.of("foo")
                ))
        );
    }

    @Test
    public void parseCommand_showTwoParameters_invalid() {
        assertEquals(
                Optional.of(new InvalidCommand(
                        2L,
                        "Exactly 1 parameter expected for command show"
                )),
                commandParsingService.parseCommand(new Command(
                        null,
                        2L,
                        1L,
                        "show",
                        List.of("provider", "foo")
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
