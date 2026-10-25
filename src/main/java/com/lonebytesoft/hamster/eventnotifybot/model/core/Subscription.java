package com.lonebytesoft.hamster.eventnotifybot.model.core;

public record Subscription(
        long chatId,
        String provider,
        String data
) {
}
