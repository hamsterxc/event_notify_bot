package com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon;

import java.util.List;

public record BabylonMovie(
        String id,
        Long datetime,
        String title,
        String description,
        String length,
        String url,
        String imageUrl,
        List<String> tags
) {
}
