package com.lonebytesoft.hamster.eventnotifybot.model.core.executablecommand;

import com.lonebytesoft.hamster.eventnotifybot.model.core.ProviderState;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.Provider;
import com.lonebytesoft.hamster.eventnotifybot.service.storage.core.StorageService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;

import java.util.Objects;
import java.util.Optional;

public record ShowCommand(
        Long chatId,
        Provider provider
) implements ExecutableCommand {

    @Override
    public boolean execute(StorageService storageService, TelegramService telegramService) {
        return getProviderState(storageService)
                .flatMap(data -> provider.getStateView(
                        data,
                        TelegramService.MAX_MESSAGE_TEXT_LENGTH,
                        TelegramService.MAX_MESSAGE_TEXT_WITH_IMAGE_LENGTH
                ))
                .map(providerView -> {
                    telegramService.sendMessage(
                            chatId,
                            providerView.imageUrl(),
                            providerView.textHtml(),
                            false
                    );
                    return true;
                })
                .orElseGet(() -> {
                    provider.getData()
                            .ifPresent(data -> storageService.setProviderState(new ProviderState(
                                    provider.name(),
                                    System.currentTimeMillis(),
                                    data
                            )));
                    return false;
                });
    }

    @Override
    public Integer estimateWriteCost(StorageService storageService) {
        return getProviderState(storageService).isPresent() ? 0 : null;
    }

    private Optional<String> getProviderState(final StorageService storageService) {
        return storageService.getProviderStates()
                .stream()
                .filter(providerState -> Objects.equals(provider.name(), providerState.provider()))
                .map(ProviderState::data)
                .findFirst();
    }

}
