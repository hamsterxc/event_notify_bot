package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.ExecutableCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

public class EventNotifyBotJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EventNotifyBotJobHandler.class);

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;
    private final int writeCostLimit;

    public EventNotifyBotJobHandler(
            final ApplicationContext applicationContext
    ) {
        this.storageService = applicationContext.getStorageService();
        this.telegramService = applicationContext.getTelegramService();
        this.commandParsingService = applicationContext.getCommandParsingService();
        this.writeCostLimit = applicationContext.getWriteCostLimit();
    }

    @Override
    public void run(Duration extraTimeout) throws Exception {
        int budget = writeCostLimit;
        final Collection<Long> telegramChatsUsed = new HashSet<>();
        final Function<Optional<Long>, Boolean> telegramChatUsedChecker = chatId -> chatId
                .map(id -> {
                    if (telegramChatsUsed.contains(id)) {
                        return false;
                    } else {
                        telegramChatsUsed.add(id);
                        return true;
                    }
                })
                .orElse(false);

        budget = process(
                budget,
                storageService.getCommands()
                        .stream()
                        .sorted(Comparator.comparing(Command::time))
                        .toList(),
                command -> telegramChatUsedChecker.apply(Optional.ofNullable(command.chatId())),
                command -> commandParsingService.parseCommand(command)
                        .orElse(null),
                command -> estimateCost(command, 1), // max of additional cost of incomplete and complete executions
                command -> {
                    storageService.removeCommand(command.id());
                    return 1;
                },
                _ -> 0,
                _ -> {},
                command -> storageService.removeCommand(command.id())
        );
        log.debug("Processed stored commands, remaining budget: {}/{}", budget, writeCostLimit);

        // to save updated settings
        if (budget < 1) {
            storageService.flush();
            return;
        } else {
            budget -= 1;
        }

        final Settings settings = storageService.getSettings();
        final Long telegramUpdatesOffset = Optional.ofNullable(settings.telegramUpdatesOffset())
                .map(offset -> offset + 1)
                .orElse(null);
        final AtomicBoolean hasTelegramPointer = new AtomicBoolean(telegramUpdatesOffset != null);
        final AtomicLong telegramPointer = new AtomicLong(telegramUpdatesOffset == null ? 0 : telegramUpdatesOffset);
        final Consumer<ParsedCommand> telegramPointerUpdater = command -> Optional.ofNullable(command.update())
                .map(Update::id)
                .ifPresent(updateId -> {
                    hasTelegramPointer.set(true);
                    telegramPointer.set(updateId);
                });
        final Consumer<ParsedCommand> commandStorageAction = command -> {
            command.command().ifPresent(storageService::putCommand);
            telegramPointerUpdater.accept(command);
        };
        budget = process(
                budget,
                telegramService.getUpdates(telegramUpdatesOffset)
                        .stream()
                        .sorted(Comparator.comparing(Update::id))
                        .map(update -> new ParsedCommand(
                                update,
                                Optional.ofNullable(update.message())
                                        .flatMap(commandParsingService::parseMessage)
                        ))
                        .toList(),
                command -> telegramChatUsedChecker.apply(command.command().map(Command::chatId)),
                command -> command.command()
                        .flatMap(commandParsingService::parseCommand)
                        .orElse(null),
                command -> estimateCost(command, 1), // max of additional cost of incomplete and complete executions
                command -> {
                    telegramPointerUpdater.accept(command);
                    return 0;
                },
                command -> {
                    commandStorageAction.accept(command);
                    return 1;
                },
                commandStorageAction,
                telegramPointerUpdater
        );
        log.debug("Processed Telegram commands, remaining budget: {}/{}", budget, writeCostLimit);

        storageService.setSettings(new Settings(
                hasTelegramPointer.get() ? telegramPointer.get() : null
        ));
        storageService.flush();
    }

    private Integer estimateCost(
            final ExecutableCommand command,
            final int additionalCost
    ) {
        return command == null
                ? Integer.valueOf(additionalCost)
                : Optional.ofNullable(command.estimateWriteCost(storageService))
                  .map(cost -> cost + additionalCost)
                  .orElse(null);
    }

    private <T> int process(
            final int budget,
            final List<T> entities,
            final Function<T, Boolean> condition,
            final Function<T, ExecutableCommand> commandConverter,
            final Function<ExecutableCommand, Integer> costCalculator,
            final Function<T, Integer> onMissingCommand,
            final Function<T, Integer> onSkippedCommand,
            final Consumer<T> onIncompleteExecution,
            final Consumer<T> onCompleteExecution
    ) {
        int remainingBudget = budget;
        for (final T entity : entities) {
            // if the unavoidable potential cost part is less than remaining budget, finish processing
            final Integer baseCost = costCalculator.apply(null);
            if ((baseCost != null) && (remainingBudget < baseCost)) {
                return remainingBudget;
            }

            if (!condition.apply(entity)) {
                // if the cost for skipping is unknown, finish processing immediately,
                // otherwise subtract it from remaining budget and proceed
                final Integer skippedCost = onSkippedCommand.apply(entity);
                if (skippedCost == null) {
                    return 0;
                } else {
                    remainingBudget -= skippedCost;
                    continue;
                }
            }

            final ExecutableCommand command = commandConverter.apply(entity);
            final Integer cost = costCalculator.apply(command);
            // if the potential processing cost is less than remaining budget, finish processing
            if ((cost != null) && (remainingBudget < cost) && (remainingBudget < budget)) {
                return remainingBudget;
            }

            if (command == null) {
                // if the cost for handling a missing command is unknown, finish processing immediately,
                // otherwise subtract it from remaining budget and proceed
                final Integer missingCost = onMissingCommand.apply(entity);
                if (missingCost == null) {
                    return 0;
                } else {
                    remainingBudget -= missingCost;
                    continue;
                }
            }

            if (cost == null) {
                // cost is unknown, executing the command only if full budget is available
                // otherwise it will be picked up later
                if (remainingBudget == budget) {
                    if (command.execute(storageService, telegramService)) {
                        onCompleteExecution.accept(entity);
                    } else {
                        onIncompleteExecution.accept(entity);
                    }
                    return 0;
                } else {
                    final Integer skippedCost = onSkippedCommand.apply(entity);
                    if (skippedCost == null) {
                        return 0;
                    } else {
                        remainingBudget -= skippedCost;
                        continue;
                    }
                }
            } else {
                // if cost is within the remaining budget, execute the command
                // otherwise execute it only if full budget is available, if not - it will be picked up later
                if (cost <= remainingBudget) {
                    if (command.execute(storageService, telegramService)) {
                        onCompleteExecution.accept(entity);
                    } else {
                        onIncompleteExecution.accept(entity);
                    }
                    remainingBudget -= cost;
                    continue;
                } else {
                    if (remainingBudget == budget) {
                        log.warn("Command execution estimated cost ({}) exceeds budget ({}), executing anyway since the full budget is still available: {}",
                                cost, budget, command);
                        if (command.execute(storageService, telegramService)) {
                            onCompleteExecution.accept(entity);
                        } else {
                            onIncompleteExecution.accept(entity);
                        }
                        return 0;
                    } else {
                        final Integer skippedCost = onSkippedCommand.apply(entity);
                        if (skippedCost == null) {
                            return 0;
                        } else {
                            remainingBudget -= skippedCost;
                            continue;
                        }
                    }
                }
            }
        }
        return remainingBudget;
    }

    private record ParsedCommand(
            Update update,
            Optional<Command> command
    ) {
    }

}
