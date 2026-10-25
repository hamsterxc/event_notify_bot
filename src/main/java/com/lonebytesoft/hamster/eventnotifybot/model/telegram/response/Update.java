package com.lonebytesoft.hamster.eventnotifybot.model.telegram.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Update(
        @JsonProperty("update_id")
        Long id,
        Message message
) {
}
