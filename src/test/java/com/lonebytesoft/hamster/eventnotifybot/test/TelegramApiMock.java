package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.User;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class TelegramApiMock extends TelegramApi {

    private final List<SentMessage> sentMessages = new ArrayList<>();

    public TelegramApiMock() {
        super(null, null, null);
    }

    @Override
    public Optional<User> getMe() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<List<Update>> getUpdates(Long offset, int batchSize, Collection<String> updateTypes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Message> sendMessage(Long chatId, String text, String parseMode, Boolean showLinkPreview) {
        this.sentMessages.add(new SentMessage(chatId, null, text));
        return Optional.empty();
    }

    @Override
    public Optional<Message> sendPhoto(Long chatId, String photoUrl, String text, String parseMode, Boolean showLinkPreview) {
        this.sentMessages.add(new SentMessage(chatId, photoUrl, text));
        return Optional.empty();
    }

    public List<SentMessage> getSentMessages() {
        return sentMessages;
    }

    public record SentMessage(
            Long chatId,
            String imageUrl,
            String text
    ) {
    }

}
