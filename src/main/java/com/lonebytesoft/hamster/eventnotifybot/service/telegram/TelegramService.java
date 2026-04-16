package com.lonebytesoft.hamster.eventnotifybot.service.telegram;

import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.User;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private static final int GET_UPDATES_BATCH_SIZE = 100;
    private static final Collection<String> GET_UPDATES_TYPES = List.of("message");
    private static final String SEND_MESSAGE_PARSE_MODE = "HTML";

    private final TelegramApi telegramApi;

    public TelegramService(
            final TelegramApi telegramApi
    ) {
        this.telegramApi = telegramApi;
    }

    public Optional<User> getMe() {
        return telegramApi.getMe();
    }

    public List<Update> getUpdates(
            final Long lastUpdate
    ) {
        final List<Update> updates = new ArrayList<>();

        Optional<List<Update>> updatesResponse;
        Long offset = lastUpdate;
        for (;;) {
            updatesResponse = telegramApi.getUpdates(offset, GET_UPDATES_BATCH_SIZE, GET_UPDATES_TYPES);
            final int size = updatesResponse.map(Collection::size).orElse(0);
            log.debug("Got {} updates starting from {}", size, offset);
            updatesResponse.ifPresent(updates::addAll);

            if (size >= GET_UPDATES_BATCH_SIZE) {
                offset = updatesResponse
                        .flatMap(response -> response
                                .stream()
                                .map(Update::id)
                                .filter(Objects::nonNull)
                                .max(Long::compareTo))
                        .orElse(null);
                log.debug("Next offset for getting updates batch: {}", offset);
                if (offset == null) {
                    log.warn("Could not calculate the next message offset: {}", updatesResponse);
                    break;
                }
            } else {
                break;
            }
        }

        return updates;
    }

    public Optional<Message> sendMessage(
            final Long chatId,
            final String imageUrl,
            final String textHtml,
            final Boolean showLinkPreview
    ) {
        return imageUrl == null
                ? telegramApi.sendMessage(chatId, textHtml, SEND_MESSAGE_PARSE_MODE, showLinkPreview)
                : telegramApi.sendPhoto(chatId, imageUrl, textHtml, SEND_MESSAGE_PARSE_MODE, showLinkPreview);
    }

}
