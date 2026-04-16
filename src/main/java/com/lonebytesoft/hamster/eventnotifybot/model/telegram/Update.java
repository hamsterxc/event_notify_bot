package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Update(
        @JsonProperty("update_id")
        Long id,
        Message message
) {
}
