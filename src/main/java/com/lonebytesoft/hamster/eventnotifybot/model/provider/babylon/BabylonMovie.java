package com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon;

import java.util.List;

public record BabylonMovie(
        String idTitle,
        String idDatetime,
        String title,
        String description,
        String datetime,
        String length,
        String url,
        String imageUrl,
        List<String> tags
) {
}
