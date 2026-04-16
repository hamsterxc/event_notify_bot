package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

public record TelegramResponse<T>(
        Boolean ok,
        T result,
        String description
) {
}
