package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record StatusCommand(
        Long chatId
) implements ExecutableCommand {

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/status_command.html");
    private static final String SUBSCRIPTIONS_PLACEHOLDER = "%SUBSCRIPTIONS%";

    private static final String SUBSCRIPTION_TEMPLATE = ResourceUtils.read("message/status_command_subscription.html");
    private static final String SUBSCRIPTION_PROVIDER_PLACEHOLDER = "%PROVIDER%";

    private static final String SUBSCRIPTIONS_EMPTY = ResourceUtils.read("message/status_command_subscriptions_empty.html");

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        final String subscriptionsValue = Optional.of(storageService.getSubscriptions()
                        .stream()
                        .filter(subscription -> Objects.equals(chatId, subscription.chatId()))
                        .toList())
                .filter(subscriptions -> !subscriptions.isEmpty())
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
        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 0;
    }

}
