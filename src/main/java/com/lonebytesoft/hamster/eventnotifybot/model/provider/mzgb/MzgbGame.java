package com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb;

public record MzgbGame(
        Long id,
        String name,
        String description,
        String imageUrl,
        String dateTime,
        String price,
        String address
) {
}
