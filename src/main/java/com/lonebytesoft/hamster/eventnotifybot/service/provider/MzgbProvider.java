package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb.MzgbGame;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb.MzgbGameCategoryDescription;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb.MzgbGameData;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.lonebytesoft.hamster.eventnotifybot.service.HttpService.isSuccess;

public class MzgbProvider implements Provider<MzgbGame> {

    private static final Logger log = LoggerFactory.getLogger(MzgbProvider.class);

    private static final String CATEGORY_DESCRIPTIONS_RESOURCE = "mzgb-descriptions.json";
    private static final String URL = "https://ber.mzgb.net";

    private final HttpService httpService;
    private final JsonMapper jsonMapper;

    public MzgbProvider(
            final HttpService httpService,
            final JsonMapper jsonMapper
    ) {
        this.httpService = httpService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<MzgbGame> get() {
        final HttpRequest startPageRequest = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .GET()
                .build();
        final Optional<HttpResponse<String>> startPageResponseOptional = httpService.fetch(startPageRequest);
        if (startPageResponseOptional.isEmpty()) {
            log.warn("Could not fetch starting page, returning empty");
            return List.of();
        }
        final HttpResponse<String> startPageResponse = startPageResponseOptional.get();
        if (!isSuccess(startPageResponse)) {
            log.warn("Unexpected starting page response code {}, returning empty", startPageResponse.statusCode());
            return List.of();
        }

        final Map<String, String> cookies = parseCookies(startPageResponse);
        final HttpRequest dataRequest = HttpRequest.newBuilder()
                .uri(URI.create(URL + "/api/load-data?page=main&locale=ru"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Cookie", cookies.entrySet()
                        .stream()
                        .map(cookie -> "%s=%s".formatted(cookie.getKey(), cookie.getValue()))
                        .collect(Collectors.joining("; ")))
                .header("X-XSRF-TOKEN", URLDecoder.decode(cookies.getOrDefault("XSRF-TOKEN", ""), StandardCharsets.UTF_8))
                .build();
        final Optional<HttpResponse<String>> dataResponseOptional = httpService.fetch(dataRequest);
        if (dataResponseOptional.isEmpty()) {
            log.warn("Could not fetch game data, returning empty");
            return List.of();
        }
        final HttpResponse<String> dataResponse = dataResponseOptional.get();
        if (!isSuccess(dataResponse)) {
            log.warn("Unexpected game data response code {}, returning empty", dataResponse.statusCode());
            return List.of();
        }

        final MzgbGameData gameData;
        try {
            gameData = jsonMapper.readValue(dataResponse.body(), MzgbGameData.class);
        } catch (JacksonException e) {
            log.warn("Could not parse game data, returning empty", e);
            return List.of();
        }

        final Map<Integer, String> categoryDescriptions = getCategoryDescriptions();
        final List<MzgbGame> games = gameData.upcomingGames()
                .stream()
                .map(upcomingGame -> new MzgbGame(
                        upcomingGame.id(),
                        Optional.ofNullable(upcomingGame.game())
                                .map(MzgbGameData.Game::name)
                                .orElse(null),
                        Optional.ofNullable(upcomingGame.game())
                                .map(MzgbGameData.Game::categoryId)
                                .map(categoryDescriptions::get)
                                .orElse(null),
                        Optional.ofNullable(upcomingGame.imageUrl())
                                .map(imageUrl -> URL + imageUrl)
                                .orElse(null),
                        Stream.of(upcomingGame.eventDate(), upcomingGame.eventTime())
                                .filter(Objects::nonNull)
                                .collect(Collectors.joining(", ")),
                        Stream.of(upcomingGame.price(), upcomingGame.currency())
                                .filter(Objects::nonNull)
                                .map(String::valueOf)
                                .collect(Collectors.joining(" ")),
                        Optional.ofNullable(upcomingGame.venue())
                                .map(venue -> Stream.of(venue.name(), venue.address())
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.joining(", ")))
                                .orElse(null)
                ))
                .toList();

        if (games.isEmpty()) {
            log.warn("Empty Mzgb games list successfully fetched - maybe something is wrong?");
        }
        return games;
    }

    private static Map<String, String> parseCookies(final HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie")
                .stream()
                .map(cookie -> cookie.split(";")[0].trim())
                .map(cookie -> cookie.split("="))
                .filter(cookie -> cookie.length == 2)
                .collect(Collectors.toMap(
                        cookie -> cookie[0].trim(),
                        cookie -> cookie[1].trim()
                ));
    }

    private Map<Integer, String> getCategoryDescriptions() {
        return jsonMapper.readValue(
                        getClass().getClassLoader().getResourceAsStream(CATEGORY_DESCRIPTIONS_RESOURCE),
                        new TypeReference<Collection<MzgbGameCategoryDescription>>(){}
                )
                .stream()
                .filter(description -> Optional.ofNullable(description.gameCategory()).orElse(0) != 0)
                .filter(description -> description.text() != null)
                .collect(Collectors.toMap(
                        MzgbGameCategoryDescription::gameCategory,
                        MzgbGameCategoryDescription::text,
                        (u, v) -> {
                            if (!Objects.equals(u, v)) {
                                log.warn("Duplicate category description found: '{}' and '{}'", u, v);
                            }
                            return u;
                        }
                ));
    }

}
