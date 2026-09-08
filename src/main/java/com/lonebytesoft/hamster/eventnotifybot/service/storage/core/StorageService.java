package com.lonebytesoft.hamster.eventnotifybot.service.storage.core;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Command;
import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Settings;
import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbReadResponse;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.dynamodb.DynamoDbWriteRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.CommandProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SettingsProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.properties.SubscriptionProperties;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.CommandRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.ProviderStateRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SettingsRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SubscriptionCacheRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.SubscriptionRecord;
import com.lonebytesoft.hamster.eventnotifybot.model.storage.record.UnknownRecord;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.dynamodb.DynamoDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final int WRITE_COST_WARN_LIMIT_PERCENTAGE = 80;

    private final DynamoDbService dynamoDbService;
    private final JsonMapper jsonMapper;
    private final int writeCostWarnLimit;

    private SettingsShadow settings = new SettingsShadow(List.of(), null);
    private CommandsShadow commands = new CommandsShadow(List.of(), null);
    private ProviderStateShadow providerState = new ProviderStateShadow(List.of());
    private SubscriptionShadow subscription = new SubscriptionShadow(List.of(), null);
    private SubscriptionCacheShadow subscriptionCache = new SubscriptionCacheShadow(List.of());
    private UnknownShadow unknown = new UnknownShadow(List.of());

    public StorageService(
            final DynamoDbService dynamoDbService,
            final JsonMapper jsonMapper,
            final int writeCostLimit
    ) {
        this.dynamoDbService = dynamoDbService;
        this.jsonMapper = jsonMapper;
        this.writeCostWarnLimit = (int) Math.ceil((double) writeCostLimit * WRITE_COST_WARN_LIMIT_PERCENTAGE / 100);
    }

    public int fetch() {
        final DynamoDbReadResponse dynamoDbReadResponse = dynamoDbService.read();

        final Map<RecordType, List<DynamoDbRecord>> records = dynamoDbReadResponse.records()
                .stream()
                .collect(Collectors.groupingBy(record -> RecordType.fromValue(record.type())));

        final Collection<DynamoDbRecord> settingsRecords = records.getOrDefault(RecordType.SETTINGS, List.of());
        if (settingsRecords.size() > 1) {
            log.warn("Multiple settings records fetched, using the latest: {}", settingsRecords);
        }
        this.settings = new SettingsShadow(settingsRecords, jsonMapper);

        this.commands = new CommandsShadow(records.getOrDefault(RecordType.COMMAND, List.of()), jsonMapper);
        this.providerState = new ProviderStateShadow(records.getOrDefault(RecordType.PROVIDER_STATE, List.of()));
        this.subscription = new SubscriptionShadow(records.getOrDefault(RecordType.SUBSCRIPTION, List.of()), jsonMapper);
        this.subscriptionCache = new SubscriptionCacheShadow(records.getOrDefault(RecordType.SUBSCRIPTION_CACHE, List.of()));

        final Collection<DynamoDbRecord> unknownRecords = records.getOrDefault(RecordType.UNKNOWN, List.of());
        if (!unknownRecords.isEmpty()) {
            log.warn("Records of unknown type fetched, ignoring: {}", unknownRecords);
        }
        this.unknown = new UnknownShadow(unknownRecords);

        return dynamoDbReadResponse.consumedCapacity();
    }

    public int cleanup(final int writeCostLimit) {
        int limitLeft = writeCostLimit;
        limitLeft -= cleanupSettings(limitLeft);
        limitLeft -= cleanupUnknown(limitLeft);
        return writeCostLimit - limitLeft;
    }

    public int flush() {
        final Collection<DynamoDbWriteRequest> writeRequests = Stream.of(
                        settings,
                        commands,
                        providerState,
                        subscription,
                        subscriptionCache,
                        unknown
                )
                .map(StorageShadow::flush)
                .flatMap(Collection::stream)
                .toList();

        final int writeCost = writeRequests.isEmpty()
                ? 0
                : dynamoDbService.write(writeRequests); // 1 WCU has write-cost of 1
        if (writeCost < writeCostWarnLimit) {
            log.debug("Flushing to storage: {} write cost", writeCost);
        } else {
            log.warn("Flushing to storage: {} write cost, more than {}% of limit", writeCost, WRITE_COST_WARN_LIMIT_PERCENTAGE);
        }

        return writeCost;
    }

    public Settings getSettings() {
        return getSettingsRecords()
                .findFirst()
                .map(SettingsRecord::properties)
                .map(properties -> new Settings(
                        properties.telegramUpdatesOffset()
                ))
                .orElseGet(() -> new Settings(
                        null
                ));
    }

    public void setSettings(final Settings settings) {
        final String id = getSettingsRecords()
                .findFirst()
                .map(SettingsRecord::id)
                .orElseGet(() -> UUID.randomUUID().toString());
        final SettingsProperties properties = new SettingsProperties(
                settings.telegramUpdatesOffset()
        );
        this.settings.put(new SettingsRecord(
                id,
                System.currentTimeMillis(),
                properties
        ));
    }

    private int cleanupSettings(final int writeCostLimit) {
        final Collection<String> cleanupIds = getSettingsRecords()
                .skip(1)
                .limit(writeCostLimit) // assuming here that removing a record has write-cost of 1
                .map(SettingsRecord::id)
                .toList();
        if (!cleanupIds.isEmpty()) {
            log.info("Cleaning up {} older settings records, leaving only the latest", cleanupIds.size());
            cleanupIds.forEach(this.settings::remove);
        }
        return cleanupIds.size();
    }

    private Stream<SettingsRecord> getSettingsRecords() {
        return this.settings.getAll()
                .stream()
                .sorted(Comparator.comparing(SettingsRecord::time).reversed()
                        .thenComparing(SettingsRecord::id));
    }

    public Collection<Command> getCommands() {
        return this.commands.getAll()
                .stream()
                .map(record -> new Command(
                        record.id(),
                        record.chatId(),
                        record.time(),
                        record.properties().command(),
                        record.properties().parameters()
                ))
                .toList();
    }

    public void putCommand(final Command command) {
        // set a random id if there was none
        final String id = Optional.ofNullable(command.id())
                .orElseGet(() -> UUID.randomUUID().toString());
        final CommandProperties properties = new CommandProperties(
                command.command(),
                command.parameters()
        );
        this.commands.put(new CommandRecord(
                id,
                command.chatId(),
                command.time(),
                properties
        ));
    }

    public void removeCommand(final String id) {
        this.commands.remove(id);
    }

    public Collection<ProviderState> getProviderStates() {
        return this.providerState.getAll()
                .stream()
                .map(record -> new ProviderState(
                        record.provider(),
                        record.time(),
                        record.data()
                ))
                .toList();
    }

    public void setProviderState(final ProviderState providerState) {
        final String id = this.providerState.getAll()
                .stream()
                .filter(record -> Objects.equals(record.provider(), providerState.provider()))
                .findFirst()
                .map(ProviderStateRecord::id)
                .orElseGet(() -> UUID.randomUUID().toString());
        this.providerState.put(new ProviderStateRecord(
                id,
                providerState.provider(),
                providerState.time(),
                providerState.data()
        ));
    }

    public Collection<Subscription> getSubscriptions() {
        final Map<String, SubscriptionCacheRecord> subscriptionCache = getSubscriptionCache();
        return this.subscription.getAll()
                .stream()
                .map(subscriptionRecord -> Optional.ofNullable(subscriptionRecord.properties())
                        .map(SubscriptionProperties::cacheRef)
                        .map(subscriptionCache::get)
                        .map(cache -> new Subscription(
                                subscriptionRecord.chatId(),
                                cache.provider(),
                                cache.data()
                        ))
                        .orElseGet(() -> {
                            log.warn("No reference cache entry for subscription, skipping: {}", subscriptionRecord);
                            return null;
                        }))
                .filter(Objects::nonNull)
                .toList();
    }

    public boolean addSubscription(final Subscription subscription) {
        if (!findSubscriptionIds(
                getSubscriptionCache(),
                subscription.chatId(),
                subscription.provider()
        ).isEmpty()) {
            log.debug("Not creating a duplicate subscription: {}", subscription);
            return false;
        }

        final String cacheRef = obtainSubscriptionCacheRef(subscription.provider(), subscription.data());
        final SubscriptionRecord subscriptionRecord = new SubscriptionRecord(
                UUID.randomUUID().toString(),
                subscription.chatId(),
                System.currentTimeMillis(),
                new SubscriptionProperties(cacheRef)
        );
        this.subscription.put(subscriptionRecord);
        log.debug("Creating a new subscription: {}", subscriptionRecord);
        return true;
    }

    public boolean updateSubscription(final Subscription subscription) {
        final Collection<String> subscriptionIds = findSubscriptionIds(
                getSubscriptionCache(),
                subscription.chatId(),
                subscription.provider()
        );
        if (subscriptionIds.isEmpty()) {
            log.warn("Not updating a non-existent subscription: {}", subscription);
            return false;
        } else {
            if (subscriptionIds.size() > 1) {
                log.warn("Multiple subscriptions found: {}", subscription);
            }

            final Long time = System.currentTimeMillis();
            final String cacheRef = obtainSubscriptionCacheRef(subscription.provider(), subscription.data());
            subscriptionIds.forEach(id -> this.subscription.put(new SubscriptionRecord(
                    id,
                    subscription.chatId(),
                    time,
                    new SubscriptionProperties(cacheRef)
            )));
            log.debug("Updating subscription: {}", subscription);
            return true;
        }
    }

    public boolean removeSubscription(
            final Long chatId,
            final String provider
    ) {
        final Collection<String> subscriptionIds = findSubscriptionIds(
                getSubscriptionCache(),
                chatId,
                provider
        );
        if (subscriptionIds.isEmpty()) {
            log.debug("Not removing a non-existent subscription: chat {}, provider {}", chatId, provider);
            return false;
        } else {
            if (subscriptionIds.size() > 1) {
                log.warn("Multiple subscriptions for chat {}, provider {}", chatId, provider);
            }

            subscriptionIds.forEach(this.subscription::remove);
            log.debug("Removing subscription: chat {}, provider {}", chatId, provider);
            return true;
        }
    }

    private Map<String, SubscriptionCacheRecord> getSubscriptionCache() {
        return this.subscriptionCache.getAll()
                .stream()
                .collect(Collectors.toMap(SubscriptionCacheRecord::id, Function.identity()));
    }

    private String obtainSubscriptionCacheRef(
            final String provider,
            final String data
    ) {
        return this.subscriptionCache.getAll()
                .stream()
                .filter(subscriptionCacheRecord -> Objects.equals(provider, subscriptionCacheRecord.provider())
                        && Objects.equals(data, subscriptionCacheRecord.data()))
                .findFirst()
                .map(SubscriptionCacheRecord::id)
                .orElseGet(() -> {
                    final String id = UUID.randomUUID().toString();
                    final SubscriptionCacheRecord subscriptionCacheRecord = new SubscriptionCacheRecord(
                            id,
                            provider,
                            System.currentTimeMillis(),
                            data
                    );
                    this.subscriptionCache.put(subscriptionCacheRecord);
                    log.debug("Creating a new subscription cache entry: {}", subscriptionCacheRecord);
                    return id;
                });
    }

    private Collection<String> findSubscriptionIds(
            final Map<String, SubscriptionCacheRecord> subscriptionCache,
            final Long chatId,
            final String provider
    ) {
        return this.subscription.getAll()
                .stream()
                .filter(subscriptionRecord -> Objects.equals(subscriptionRecord.chatId(), chatId))
                .map(subscriptionRecord -> Optional.ofNullable(subscriptionRecord.properties())
                        .map(SubscriptionProperties::cacheRef)
                        .map(subscriptionCache::get)
                        .filter(subscriptionCacheRecord -> Objects.equals(subscriptionCacheRecord.provider(), provider))
                        .map(_ -> subscriptionRecord.id())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private int cleanupUnknown(final int writeCostLimit) {
        final Collection<String> cleanupIds = this.unknown.getAll()
                .stream()
                .limit(writeCostLimit) // assuming here that removing a record has write-cost of 1
                .map(UnknownRecord::id)
                .toList();
        if (!cleanupIds.isEmpty()) {
            log.info("Cleaning up {} records of unknown type", cleanupIds.size());
            cleanupIds.forEach(this.unknown::remove);
        }
        return cleanupIds.size();
    }

}
