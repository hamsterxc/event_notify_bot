package com.lonebytesoft.hamster.eventnotifybot.service.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.CommandType;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.ExecutableCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.HelpCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.InvalidCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.StatusCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.UnknownCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParsingService {

    private static final Logger log = LoggerFactory.getLogger(CommandParsingService.class);

    private static final Pattern TELEGRAM_MESSAGE_PATTERN = Pattern.compile("^/([A-Za-z0-9_]+)(@hamster_event_bot)?(\\s+(.*))?$");
    private static final int TELEGRAM_MESSAGE_MAX_LENGTH = 500;
    private static final String TELEGRAM_CHAT_TYPE_DIRECT = "private";

    private static final Collection<CommandType> UNKNOWN_COMMANDS = EnumSet.of(CommandType.INVALID, CommandType.UNKNOWN);

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

        final CommandType commandType = CommandType.fromValue(messageParts.command());
        final boolean isDirect = (messageParts.address() != null)
                || TELEGRAM_CHAT_TYPE_DIRECT.equalsIgnoreCase(message.chat().type());
        final boolean isUnknown = UNKNOWN_COMMANDS.contains(commandType);
        final boolean isTooLong = message.text().length() > TELEGRAM_MESSAGE_MAX_LENGTH;
        if (isTooLong) {
            // shielding against too large writes to the storage
            if (isDirect || !isUnknown) {
                log.debug("Too long command received: {}", message);
                return Optional.of(new Command(
                        null,
                        chatId,
                        message.date(),
                        CommandType.INVALID.getValue(),
                        List.of("Command too long")
                ));
            } else {
                return Optional.empty();
            }
        } else if (isUnknown) {
            if (isDirect) {
                log.debug("Unknown command received: {}", message);
                return Optional.of(new Command(
                        null,
                        chatId,
                        message.date(),
                        CommandType.UNKNOWN.getValue(),
                        List.of(messageParts.command())
                ));
            } else {
                return Optional.empty();
            }
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
        return Optional.of(switch (CommandType.fromValue(command.command())) {
            case TEST -> new UnknownCommand(command.chatId(), command.command());
            case START, HELP -> command.parameters().isEmpty()
                    ? new HelpCommand(command.chatId())
                    : noParametersExpected(command);
            case STATUS -> command.parameters().isEmpty()
                    ? new StatusCommand(command.chatId())
                    : noParametersExpected(command);
            case INVALID -> new InvalidCommand(command.chatId(), command.parameters().getFirst());
            case UNKNOWN -> new UnknownCommand(command.chatId(), command.parameters().getFirst());
        });
    }

    private static ExecutableCommand noParametersExpected(final Command command) {
        return new InvalidCommand(command.chatId(), "No parameters expected for command " + command.command());
    }

    private record MessageParts(
            String command,
            String address,
            String parameters
    ) {
    }

}
