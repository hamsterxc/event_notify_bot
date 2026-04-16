package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Message(
        @JsonProperty("message_id")
        Long id,
        Chat chat,
        String text
) {
}
