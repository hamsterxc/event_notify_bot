package com.lonebytesoft.hamster.eventnotifybot.model.core;

public record Subscription(
        Long chatId,
        String provider,
        String data
) {
}
