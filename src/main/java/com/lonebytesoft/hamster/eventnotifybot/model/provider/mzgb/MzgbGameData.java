package com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MzgbGameData(
        List<UpcomingGame> upcomingGames
) {

    public record UpcomingGame(
            Long id,
            @JsonProperty("event_date")
            String eventDate,
            @JsonProperty("event_time")
            String eventTime,
            Game game,
            @JsonProperty("img")
            String imageUrl,
            Integer price,
            String currency,
            Venue venue
    ) {
    }

    public record Game(
            String name,
            @JsonProperty("category_id")
            Integer categoryId
    ) {
    }

    public record Venue(
            String address,
            String name
    ) {
    }

}
