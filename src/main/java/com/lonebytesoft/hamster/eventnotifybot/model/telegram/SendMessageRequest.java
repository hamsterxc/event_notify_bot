package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendMessageRequest(
        @JsonProperty("chat_id")
        Long chatId,
        String text,
        @JsonProperty("parse_mode")
        String parseMode,
        @JsonProperty("link_preview_options")
        LinkPreviewOptions linkPreviewOptions
) {
}
