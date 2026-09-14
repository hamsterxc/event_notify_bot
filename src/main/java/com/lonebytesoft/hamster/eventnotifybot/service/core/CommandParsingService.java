package com.lonebytesoft.hamster.eventnotifybot.service.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.CommandType;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.ExecutableCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.InvalidCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.UnknownCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParsingService {

    private static final Logger log = LoggerFactory.getLogger(CommandParsingService.class);

    private static final Pattern TELEGRAM_MESSAGE_PATTERN = Pattern.compile("^/([A-Za-z0-9_]+)(@hamster_event_bot)?(\\s+(.*))?$");
    private static final int TELEGRAM_MESSAGE_MAX_LENGTH = 500;
    private static final String TELEGRAM_CHAT_TYPE_DIRECT = "private";

    private static final String UNKNOWN_COMMAND = "unknown";
    private static final String INVALID_COMMAND = "invalid";

    public Optional<Command> parseMessage(final Message message) {
        final MessageParts messageParts = Optional.ofNullable(message)
                .map(Message::text)
                .map(TELEGRAM_MESSAGE_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> new MessageParts(matcher.group(1), matcher.group(2), matcher.group(4)))
                .orElse(null);
        if (messageParts == null) {
            log.debug("Irrelevant message received: {}", message);
            return Optional.empty();
        }

        final Long chatId = Optional.ofNullable(message.chat())
                .map(Chat::id)
                .orElse(null);
        if (chatId == null) {
            log.debug("Message with unknown chat received: {}", message);
            return Optional.empty();
        }

        final CommandType commandType = CommandType.fromValue(messageParts.command())
                .orElse(null);
        if (commandType == null) {
            final boolean isDirect = (messageParts.address() != null)
                    || TELEGRAM_CHAT_TYPE_DIRECT.equalsIgnoreCase(message.chat().type());
            log.debug("Unknown command received (direct {}): {}", isDirect, message);
            if (isDirect) {
                return Optional.of(new Command(
                        null,
                        chatId,
                        message.date(),
                        UNKNOWN_COMMAND,
                        List.of(messageParts.command()) // todo: this can be too long too
                ));
            } else {
                return Optional.empty();
            }
        }

        // shielding against too large writes to the storage
        if (message.text().length() > TELEGRAM_MESSAGE_MAX_LENGTH) {
            log.debug("Too long command received: {}", message);
            return Optional.of(new Command(
                    null,
                    chatId,
                    message.date(),
                    INVALID_COMMAND,
                    List.of("Command too long")
            ));
        }

        final List<String> parameters = Optional.ofNullable(messageParts.parameters())
                .map(parametersPart -> parametersPart.split("\\s+"))
                .stream()
                .flatMap(Arrays::stream)
                .map(String::trim)
                .toList();
        final Command command = new Command(
                null,
                chatId,
                message.date(),
                commandType.getValue(),
                parameters
        );
        log.debug("Parsed command message: {} -> {}", message, command);
        return Optional.of(command);
    }

    public Optional<ExecutableCommand> parseCommand(final Command command) {
        return Optional.ofNullable(switch (command.command()) {
            case UNKNOWN_COMMAND -> new UnknownCommand(command.chatId(), command.parameters().getFirst());
            case INVALID_COMMAND -> new InvalidCommand(command.chatId(), command.parameters().getFirst());
            case null, default -> {
                log.warn("Processing unknown command: {}", command);
                yield null;
            }
        });
    }

    private record MessageParts(
            String command,
            String address,
            String parameters
    ) {
    }

}
