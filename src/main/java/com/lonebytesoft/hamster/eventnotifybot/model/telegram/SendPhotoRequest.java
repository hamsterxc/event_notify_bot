package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SendPhotoRequest(
        @JsonProperty("chat_id")
        Long chatId,
        @JsonProperty("photo")
        String photoUrl,
        String caption,
        @JsonProperty("parse_mode")
        String parseMode,
        @JsonProperty("link_preview_options")
        LinkPreviewOptions linkPreviewOptions
) {
}
