package com.lonebytesoft.hamster.eventnotifybot.model.telegram.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Message(
        @JsonProperty("message_id")
        Long id,
        Long date,
        Chat chat,
        String text
) {
}
