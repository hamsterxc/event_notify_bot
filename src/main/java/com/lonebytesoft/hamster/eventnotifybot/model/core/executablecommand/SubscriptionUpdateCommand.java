package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.Subscription;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SubscriptionUpdateCommand(
        long chatId,
        Provider provider,
        String dataOld,
        String dataNew
) implements ExecutableCommand {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionUpdateCommand.class);

    private static final String MESSAGE_TEMPLATE = ResourceUtils.read("message/command/subscription_update.html");
    private static final String PROVIDER_PLACEHOLDER = "%PROVIDER%";
    private static final String CONTENT_PLACEHOLDER = "%CONTENT%";

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        if (!storageService.updateSubscription(getSubscription())) {
            log.warn("Not updating a non-existent subscription: chat {}, provider '{}'", chatId, provider.name());
            return true;
        }

        final String template = MESSAGE_TEMPLATE
                .replace(PROVIDER_PLACEHOLDER, provider.name())
                .trim();
        final int reserved = template.replace(CONTENT_PLACEHOLDER, "").length();
        provider.getDiffView(
                        dataOld,
                        dataNew,
                        TelegramService.MAX_MESSAGE_TEXT_LENGTH - reserved,
                        TelegramService.MAX_MESSAGE_TEXT_WITH_IMAGE_LENGTH - reserved
                )
                .ifPresent(providerView -> {
                    telegramService.sendMessage(
                            chatId,
                            providerView.imageUrl(),
                            template.replace(CONTENT_PLACEHOLDER, providerView.textHtml()),
                            false
                    );
                    log.debug("Sent '{}' provider update to chat {}", provider.name(), chatId);
                });

        return true;
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return storageService.estimateWriteCost(getSubscription());
    }

    private Subscription getSubscription() {
        return new Subscription(
                chatId,
                provider.name(),
                dataNew
        );
    }

}
