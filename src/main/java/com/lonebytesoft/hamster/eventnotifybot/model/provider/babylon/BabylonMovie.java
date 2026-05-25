package com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon;

import java.util.Collection;

public record BabylonMovie(
        String idTitle,
        String idDatetime,
        String title,
        String description,
        String datetime,
        String length,
        String url,
        String imageUrl,
        Collection<String> tags
) {
}
