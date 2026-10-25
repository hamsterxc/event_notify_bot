package com.lonebytesoft.hamster.eventnotifybot.handler;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.ExecutableCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand.SubscriptionUpdateCommand;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.core.CommandParsingService;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventNotifyBotJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(EventNotifyBotJobHandler.class);

    private final StorageService storageService;
    private final TelegramService telegramService;
    private final CommandParsingService commandParsingService;
    private final int writeCostLimit;
    private final InstantSource instantSource;
    private final Collection<Provider> providers;

    public EventNotifyBotJobHandler(
            final ApplicationContext applicationContext
    ) {
        this.storageService = applicationContext.getStorageService();
        this.telegramService = applicationContext.getTelegramService();
        this.commandParsingService = applicationContext.getCommandParsingService();
        this.writeCostLimit = applicationContext.getWriteCostLimit();
        this.instantSource = applicationContext.getInstantSource();
        this.providers = applicationContext.getProviders();
    }

    @Override
    public void run(Duration extraTimeout) throws Exception {
        int budget = writeCostLimit;
        final TelegramChatsTracker telegramChatsTracker = new TelegramChatsTracker();

        budget -= processCommands(writeCostLimit, telegramChatsTracker);
        log.debug("Processed commands, budget remaining: {}/{}", budget, writeCostLimit);

        for (final Provider provider : getUpdatableProviders()) {
            final String providerName = provider.name();
            // updating provider state only with full budget
            if (budget == writeCostLimit) {
                provider.getData()
                        .ifPresent(data -> {
                            final Instant nextUpdateTime = provider.getNextUpdateTime();
                            storageService.setProviderState(new ProviderState(
                                    providerName,
                                    Optional.ofNullable(nextUpdateTime).map(Instant::getEpochSecond).orElse(null),
                                    data
                            ));
                            log.info("Updated '{}' provider state, next update at {}", providerName, nextUpdateTime);
                        });
                budget -= storageService.flush();
            } else {
                log.debug("Updating '{}' provider state on the next run, when full budget is available", providerName);
                return;
            }
        }
        log.debug("Updated provider states, budget remaining: {}/{}", budget, writeCostLimit);

        budget -= sendSubscriptionUpdates(budget, telegramChatsTracker);
        log.debug("Processed provider updates, budget remaining: {}/{}", budget, writeCostLimit);
    }

    /**
     * Process both stored and Telegram commands, keeping track of write budget and Telegram chats accessed.
     * @param budget write cost budget
     * @param telegramChatsTracker the tracker of Telegram chats accessed
     * @return write cost actually consumed
     */
    private int processCommands(
            final int budget,
            final TelegramChatsTracker telegramChatsTracker
    ) {
        int remainingBudget = budget;

        remainingBudget = process(
                remainingBudget,
                storageService.getCommands()
                        .stream()
                        .sorted(Comparator.comparing(Command::time))
                        .toList(),
                command -> telegramChatsTracker.isAllowed(command.chatId()),
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
        log.debug("Processed stored commands, estimated budget remaining: {}/{}", remainingBudget, budget);

        // to potentially save the updated settings
        if (remainingBudget < 1) {
            return storageService.flush();
        } else {
            remainingBudget -= 1;
        }

        final Settings settings = storageService.getSettings();
        final TelegramUpdatesTracker telegramUpdatesTracker = new TelegramUpdatesTracker(settings.telegramLastUpdateId());
        remainingBudget = process(
                remainingBudget,
                telegramService.getUpdates(telegramUpdatesTracker.getUpdateId())
                        .stream()
                        .sorted(Comparator.comparing(Update::id))
                        .map(update -> new ParsedCommand(
                                update,
                                Optional.ofNullable(update.message())
                                        .flatMap(commandParsingService::parseMessage)
                        ))
                        .toList(),
                command -> command.command()
                        .map(Command::chatId)
                        .map(telegramChatsTracker::isAllowed)
                        .orElse(false),
                command -> command.command()
                        .flatMap(commandParsingService::parseCommand)
                        .orElse(null),
                command -> estimateCost(command, 1), // max of additional cost of incomplete and complete executions
                command -> {
                    telegramUpdatesTracker.accept(command.update());
                    return 0;
                },
                command -> {
                    command.command().ifPresent(storageService::addCommand);
                    telegramUpdatesTracker.accept(command.update());
                    return 1;
                },
                command -> {
                    command.command().ifPresent(storageService::addCommand);
                    telegramUpdatesTracker.accept(command.update());
                },
                command -> {
                    telegramUpdatesTracker.accept(command.update());
                }
        );
        log.debug("Processed Telegram commands, estimated budget remaining: {}/{}", remainingBudget, budget);

        storageService.setSettings(new Settings(
                telegramUpdatesTracker.getUpdateId()
        ));
        return storageService.flush();
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

    /**
     * Process the entities (commands), keeping track of write budget.
     * All cost calculators must return the upper bound of the cost.
     * @param budget write cost budget
     * @param entities entities to process
     * @param condition the guard signalling whether the entity can be processed
     * @param commandConverter the converter of an entity into an {@link ExecutableCommand}
     * @param costCalculator the calculator of cost of the {@link ExecutableCommand}
     * @param onMissingCommand the action to execute in case an entity could not be converted to an  {@link ExecutableCommand}, returning the cost of this action
     * @param onSkippedCommand the action to execute in case the {@link ExecutableCommand} must be skipped, returning the cost of this action
     * @param onIncompleteExecution the action to execute in case the {@link ExecutableCommand} has not finished its execution yet (see {@link ExecutableCommand#execute})
     * @param onCompleteExecution the action to execute in case the {@link ExecutableCommand} has finished its execution
     * @return the remaining estimated budget
     * @param <T> the entity type
     */
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

    /**
     * Return providers that:
     * - have active subscriptions attached,
     * - have no state or has its state already due to being updated.
     * Sorted by the earliest previous update time.
     */
    private List<Provider> getUpdatableProviders() {
        final Collection<String> activeProviders = storageService.getSubscriptions()
                .stream()
                .map(Subscription::provider)
                .collect(Collectors.toSet());
        final Map<String, Optional<Long>> providerTimes = storageService.getProviderStates()
                .stream()
                .collect(Collectors.toMap(
                        ProviderState::provider,
                        providerState -> Optional.ofNullable(providerState.time())
                ));
        final long now = instantSource.instant().getEpochSecond();
        return providers
                .stream()
                .filter(provider -> activeProviders.contains(provider.name()))
                .map(provider -> {
                    final Optional<Long> providerTime = providerTimes.get(provider.name());
                    if ((providerTime == null) // no state exists yet
                            || providerTime.map(time -> time < now).orElse(false) // or the state is stale (null never expires)
                    ) {
                        return Map.entry(
                                provider,
                                Optional.ofNullable(providerTime)
                                        .flatMap(Function.identity())
                        );
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        providerWithTime -> providerWithTime.getValue().orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(Map.Entry::getKey)
                .toList();
    }

    private int sendSubscriptionUpdates(
            final int budget,
            final TelegramChatsTracker telegramChatsTracker
    ) {
        final Map<String, Provider> providers = this.providers
                .stream()
                .collect(Collectors.toMap(Provider::name, Function.identity()));
        final Map<String, String> providerStates = storageService.getProviderStates()
                .stream()
                .collect(Collectors.toMap(ProviderState::provider, ProviderState::data));
        process(
                budget,
                storageService.getSubscriptions()
                        .stream()
                        .filter(subscription -> providers.containsKey(subscription.provider()))
                        .filter(subscription -> !Objects.equals(subscription.data(), providerStates.get(subscription.provider())))
                        .sorted(Comparator.comparing(Subscription::chatId)
                                .thenComparing(Comparator.nullsLast(Comparator.comparing(Subscription::provider))))
                        .toList(),
                subscription -> telegramChatsTracker.isAllowed(subscription.chatId()),
                subscription -> new SubscriptionUpdateCommand(
                        subscription.chatId(),
                        providers.get(subscription.provider()),
                        subscription.data(),
                        providerStates.get(subscription.provider())
                ),
                command -> estimateCost(command, 0),
                _ -> 0, // command is always available
                _ -> 0, // do nothing if command is skipped
                _ -> {}, // command always completes
                _ -> {} // do nothing when command completes
        );
        return storageService.flush();
    }

    private record ParsedCommand(
            Update update,
            Optional<Command> command
    ) {
    }

    private static class TelegramChatsTracker {

        private final Collection<Long> chatsUsed = new HashSet<>();

        public boolean isAllowed(final long chatId) {
            if (chatsUsed.contains(chatId)) {
                return false;
            } else {
                chatsUsed.add(chatId);
                return true;
            }
        }

    }

    private static class TelegramUpdatesTracker implements Consumer<Update> {

        private Long updateId;

        private TelegramUpdatesTracker(final Long updateId) {
            this.updateId = updateId;
        }

        @Override
        public void accept(Update update) {
            Optional.ofNullable(update)
                    .map(Update::id)
                    .ifPresent(updateId -> this.updateId = this.updateId == null
                            ? updateId
                            : Math.max(this.updateId, updateId));
        }

        public Long getUpdateId() {
            return updateId;
        }

    }

}
