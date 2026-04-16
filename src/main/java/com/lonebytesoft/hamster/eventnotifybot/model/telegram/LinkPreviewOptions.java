package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LinkPreviewOptions(
        @JsonProperty("is_disabled")
        Boolean isDisabled
) {
}
