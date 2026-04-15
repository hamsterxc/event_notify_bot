package com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.Fingerprintable;

public record MzgbGame(
        Long id,
        String name,
        String description,
        String imageUrl,
        String dateTime,
        String price,
        String address
) implements Fingerprintable {

    @Override
    public String getFingerprint() {
        return String.valueOf(id);
    }

}
