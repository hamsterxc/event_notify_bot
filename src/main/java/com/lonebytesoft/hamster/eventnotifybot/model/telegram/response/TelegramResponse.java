package com.lonebytesoft.hamster.eventnotifybot.model.telegram.response;

public record TelegramResponse<T>(
        Boolean ok,
        T result,
        String description
) {
}
