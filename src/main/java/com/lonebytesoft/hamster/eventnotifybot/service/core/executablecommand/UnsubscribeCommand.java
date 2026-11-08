package com.lonebytesoft.hamster.eventnotifybot.service.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

public record UnsubscribeCommand(
        long chatId,
        String providerName
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(UnsubscribeCommand.class);

    private static final String RESOURCE_FOLDER = "message/command";
    private static final String MESSAGE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/unsubscribe.html").trim();
    private static final String MULTIPLE_SUBSCRIPTIONS_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/unsubscribe_multiple_subscriptions.html").trim();
    private static final String NO_ACTIVE_SUBSCRIPTION_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/unsubscribe_no_active_subscription.html").trim();
    private static final String NO_ACTIVE_SUBSCRIPTIONS_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/unsubscribe_no_active_subscriptions.html").trim();
    private static final String PROVIDER_PLACEHOLDER = "%PROVIDER%";

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        if (providerName == null) {
            final Collection<String> existingProviderNames = storageService.getSubscriptions()
                    .stream()
                    .filter(subscription -> subscription.chatId() == chatId)
                    .map(Subscription::provider)
                    .collect(Collectors.toSet());
            switch (existingProviderNames.size()) {
                case 0 -> sendErrorMessage(storageService, telegramService, NO_ACTIVE_SUBSCRIPTIONS_TEMPLATE);
                case 1 -> {
                    final String existingProviderName = existingProviderNames.iterator().next();
                    storageService.removeSubscription(chatId, existingProviderName);
                    sendSuccessMessage(telegramService, existingProviderName);
                }
                default -> sendErrorMessage(storageService, telegramService, MULTIPLE_SUBSCRIPTIONS_TEMPLATE);
            }
        } else {
            if (storageService.removeSubscription(chatId, providerName)) {
                sendSuccessMessage(telegramService, providerName);
            } else {
                sendErrorMessage(storageService, telegramService, NO_ACTIVE_SUBSCRIPTION_TEMPLATE.replace(PROVIDER_PLACEHOLDER, providerName));
            }
        }
        return true;
    }

    private void sendErrorMessage(
            final StorageService storageService,
            final TelegramService telegramService,
            final String error
    ) {
        new InvalidCommand(chatId, error)
                .execute(storageService, telegramService);
    }

    private void sendSuccessMessage(
            final TelegramService telegramService,
            final String providerName
    ) {
        telegramService.sendMessage(
                chatId,
                null,
                MESSAGE_TEMPLATE.replace(PROVIDER_PLACEHOLDER, providerName),
                false
        );
        log.debug("Sent unsubscribe success message to chat {}", chatId);
    }

    /**
     * Cautiously reporting {@code 1} regardless of the actual subscriptions.
     */
    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return 1;
    }

}
