package com.lonebytesoft.hamster.eventnotifybot.model.core;

public record ProviderState(
        String provider,
        Long time,
        String data
) {
}
