package com.lonebytesoft.hamster.eventnotifybot.model.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;

public record GetUpdatesRequest(
        Long offset,
        Integer limit,
        Integer timeout,
        @JsonProperty("allowed_updates")
        Collection<String> allowedUpdates
) {
}
