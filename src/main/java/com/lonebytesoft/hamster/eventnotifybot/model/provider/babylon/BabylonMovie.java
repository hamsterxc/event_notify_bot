package com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.Fingerprintable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
) implements Fingerprintable {

    @Override
    public String getFingerprint() {
        return Stream.of(idTitle, idDatetime)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .map(s -> s.replaceAll("[^a-z0-9-]", "-"))
                .collect(Collectors.joining("-"));
    }

}
