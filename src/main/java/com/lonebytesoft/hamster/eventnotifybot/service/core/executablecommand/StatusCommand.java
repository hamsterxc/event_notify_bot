package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record StatusCommand(
        long chatId
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(StatusCommand.class);

    private static final String RESOURCE_FOLDER = "message/command";

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/status.html");
    private static final String SUBSCRIPTIONS_PLACEHOLDER = "%SUBSCRIPTIONS%";

    private static final String SUBSCRIPTION_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/status_subscription.html");
    private static final String SUBSCRIPTION_PROVIDER_PLACEHOLDER = "%PROVIDER%";

    private static final String SUBSCRIPTIONS_EMPTY = ResourceUtils.read(RESOURCE_FOLDER + "/status_subscriptions_empty.html");

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        final Optional<List<Subscription>> nonEmptySubscriptions = Optional.of(storageService.getSubscriptions()
                        .stream()
                        .filter(subscription -> chatId == subscription.chatId())
                        .toList())
                .filter(subscriptions -> !subscriptions.isEmpty());
        final String subscriptionsValue = nonEmptySubscriptions
                .map(subscriptions -> subscriptions
                        .stream()
                        .map(subscription -> SUBSCRIPTION_TEMPLATE
                                .replace(SUBSCRIPTION_PROVIDER_PLACEHOLDER, subscription.provider())
                                .trim())
                        .sorted() // stable sorting
                        .collect(Collectors.joining("\n")) // not System.lineSeparator() as this will be sent directly to Telegram
                )
                .orElse(SUBSCRIPTIONS_EMPTY);

        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE
                        .replace(SUBSCRIPTIONS_PLACEHOLDER, subscriptionsValue)
                        .trim(),
                false
        );
        if (log.isTraceEnabled()) {
            log.trace("Sent current subscriptions state to chat {}: {}", chatId, nonEmptySubscriptions);
        } else {
            log.debug("Sent current subscriptions state to chat {}", chatId);
        }
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
