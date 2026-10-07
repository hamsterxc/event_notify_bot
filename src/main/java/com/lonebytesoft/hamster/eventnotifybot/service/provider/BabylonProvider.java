package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon.BabylonMovie;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lonebytesoft.hamster.eventnotifybot.service.HttpService.isSuccess;

public class BabylonProvider implements Provider {

    private static final Logger log = LoggerFactory.getLogger(BabylonProvider.class);

    private static final String URL = "https://babylonberlin.eu";
    private static final String URL_SCHEDULE = "/programm";

    private static final TypeReference<List<BabylonMovie>> DATA_TYPE = new TypeReference<>(){};

    private static final ZoneId DATETIME_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATETIME_FORMAT_INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(DATETIME_ZONE);

    private static final String RESOURCE_FOLDER = "message/provider/babylon";

    private final HttpService httpService;
    private final JsonMapper jsonMapper;

    public BabylonProvider(
            final HttpService httpService,
            final JsonMapper jsonMapper
    ) {
        this.httpService = httpService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return "babylon";
    }

    @Override
    public Optional<String> getData() {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL + URL_SCHEDULE))
                .GET()
                .build();
        final Optional<HttpResponse<String>> responseOptional = httpService.fetch(request);
        if (responseOptional.isEmpty()) {
            log.warn("Could not fetch movies page, returning empty");
            return Optional.empty();
        }
        final HttpResponse<String> response = responseOptional.get();
        if (!isSuccess(response)) {
            log.warn("Unexpected movies page response code {}, returning empty", response.statusCode());
            return Optional.empty();
        }

        final List<BabylonMovie> movies = Jsoup.parse(response.body())
                .select("section#g-container-main div.g-content ul > li.mix")
                .stream()
                .map(element -> {
                    final Element linkElement = element.selectFirst("> div.inner-mix > h3 > a.mix-title");
                    final String id = getAttribute(element, "data-title").orElse(null);
                    return new BabylonMovie(
                            id,
                            getAttribute(element, "data-date")
                                    .map(datetime -> {
                                        try {
                                            return DATETIME_FORMAT_INPUT.parse(datetime);
                                        } catch (DateTimeParseException e) {
                                            log.warn("Could not parse '{}' movie date '{}': {}", id, datetime, e.getMessage());
                                            return null;
                                        }
                                    })
                                    .map(datetime -> Instant.from(datetime).getEpochSecond())
                                    .orElse(null),
                            getOwnText(linkElement).orElse(null),
                            getOwnText(element.selectFirst("> div.inner-mix > div.mix-introtext-outer > p.mix-introtext")).orElse(null),
                            getOwnText(element.selectFirst("> div.inner-mix > div.mix-extra > p.mix-date > span.runtime")).orElse(null),
                            getAttribute(linkElement, "href").map(link -> URL + link).orElse(null),
                            getAttribute(element.selectFirst("> div.upper-mix > a > img"), "src").orElse(null),
                            getAttribute(element, "class")
                                    .map(elementClass -> Arrays.stream(elementClass.split("\\s")))
                                    .stream()
                                    .flatMap(Function.identity())
                                    .map(String::trim)
                                    .filter(elementClass -> !elementClass.isEmpty())
                                    .filter(elementClass -> elementClass.startsWith("tag-"))
                                    .map(elementClass -> elementClass.substring("tag-".length()))
                                    .distinct()
                                    .toList()
                    );
                })
                .toList();

        if (movies.isEmpty()) {
            log.warn("Empty Babylon movies list successfully fetched - maybe something is wrong?");
        }
        return Optional.ofNullable(jsonMapper.writeValueAsString(movies));
    }

    private Optional<String> getAttribute(final Element element, final String attributeKey) {
        return Optional.ofNullable(element)
                .map(e -> e.attribute(attributeKey))
                .map(Attribute::getValue)
                .filter(attribute -> !attribute.isEmpty());
    }

    private Optional<String> getOwnText(final Element element) {
        return Optional.ofNullable(element)
                .map(Element::ownText);
    }

    @Override
    public Optional<ProviderView> getStateView(String data, Integer limit, Integer limitWithImage) {
        final List<BabylonMovie> movies;
        try {
            movies = jsonMapper.readValue(data, DATA_TYPE);
        } catch (JacksonException e) {
            log.warn("Could not parse movies: {}", data);
            return Optional.of(ViewBuilder.getStateErrorView());
        }

        return Optional.of(ViewBuilder.getStateView(movies, limit, limitWithImage));
    }

    @Override
    public Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage) {
        // todo
        throw new UnsupportedOperationException();
    }

    private static class ViewBuilder {

        private static final String DELIMITER = "\n\n";

        private static final String FOOTER_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer.html").trim();
        private static final String FOOTER_CONTENT_PLACEHOLDER = "%CONTENT%";
        private static final String FOOTER_DELIMITER = " ";

        private static final String ERROR_VIEW = ResourceUtils.read(RESOURCE_FOLDER + "/error.html").trim();

        private static final String NO_MOVIES_VIEW = ResourceUtils.read(RESOURCE_FOLDER + "/no_movies.html").trim();

        private static final String FOOTER_MORE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_more.html").trim();
        private static final String FOOTER_MORE_COUNT_PLACEHOLDER = "%COUNT%";

        private static final String FOOTER_TOTAL_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_total.html").trim();
        private static final String FOOTER_TOTAL_COUNT_PLACEHOLDER = "%COUNT%";

        private static final String FULL_LIST_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/full_list.html").trim();
        private static final String FULL_LIST_URL_PLACEHOLDER = "%URL%";
        private static final String FULL_LIST_VIEW = FULL_LIST_TEMPLATE.replace(FULL_LIST_URL_PLACEHOLDER, URL + URL_SCHEDULE);

        private static final ProviderView STATE_ERROR_VIEW = new ProviderView(
                null,
                getViewText(ERROR_VIEW, getFooterElements(null, null, 0))
        );
        private static final ProviderView STATE_EMPTY_VIEW = new ProviderView(
                null,
                getViewText(NO_MOVIES_VIEW, getFooterElements(null, null, 0))
        );

        public static ProviderView getStateErrorView() {
            return STATE_ERROR_VIEW;
        }

        public static ProviderView getStateView(
                final List<BabylonMovie> movies,
                final Integer limit,
                final Integer limitWithImage
        ) {
            if (movies.isEmpty()) {
                return STATE_EMPTY_VIEW;
            }

            final int size = movies.size();
            final int reserved = getViewText(DELIMITER, getFooterElements(FOOTER_MORE_TEMPLATE, FOOTER_MORE_COUNT_PLACEHOLDER, size)).length() - DELIMITER.length();
            final ProviderViewBuilder viewBuilder = new ProviderViewBuilder(
                    DELIMITER,
                    limit == null ? null : limit - reserved,
                    limitWithImage == null ? null : limitWithImage - reserved
            );
            int leftover = size;
            for (; leftover > 0; leftover--) {
                final BabylonMovie movie = movies.get(size - leftover);
                final Function<Integer, String> limitedView = currentLimit -> MovieViewBuilder.getMovieView(movie, null, currentLimit);
                if (!viewBuilder.append(limitedView, movie.imageUrl())) {
                    break;
                }
            }

            final ProviderView bodyView = viewBuilder.build();
            return new ProviderView(
                    bodyView.imageUrl(),
                    getViewText(bodyView.textHtml(), leftover == size
                            ? getFooterElements(FOOTER_TOTAL_TEMPLATE, FOOTER_TOTAL_COUNT_PLACEHOLDER, leftover)
                            : getFooterElements(FOOTER_MORE_TEMPLATE, FOOTER_MORE_COUNT_PLACEHOLDER, leftover))
            );
        }

        private static List<String> getFooterElements(
                final String template,
                final String countPlaceholder,
                final int count
        ) {
            return List.of(
                    count > 0
                            ? template.replace(countPlaceholder, String.valueOf(count))
                            : "",
                    FULL_LIST_VIEW
            );
        }

        private static String getViewText(
                final String body,
                final List<String> footerElements
        ) {
            return join(
                    DELIMITER,
                    List.of(
                            Optional.ofNullable(body).orElse(""),
                            FOOTER_TEMPLATE.replace(FOOTER_CONTENT_PLACEHOLDER, join(FOOTER_DELIMITER, footerElements))
                    )
            );
        }

        private static String join(
                final String delimiter,
                final List<String> strings
        ) {
            return strings
                    .stream()
                    .filter(string -> (string != null) && !string.isEmpty())
                    .collect(Collectors.joining(delimiter));
        }

    }

    private static class MovieViewBuilder {

        private static final String TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/movie.html");
        private static final String BADGE_PLACEHOLDER = "%BADGE_WITH_SPACE%";
        private static final String URL_PLACEHOLDER = "%URL%";
        private static final String TITLE_PLACEHOLDER = "%TITLE%";
        private static final String DESCRIPTION_PLACEHOLDER = "%DESCRIPTION_WITH_LF%";
        private static final String DATETIME_PLACEHOLDER = "%DATETIME_WITH_LF%";
        private static final String LENGTH_PLACEHOLDER = "%LENGTH%";
        private static final String TAGS_PLACEHOLDER = "%TAGS%";

        private static final DateTimeFormatter DATETIME_FORMAT_VIEW = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy, HH:mm").withZone(DATETIME_ZONE);
        private static final int MIN_DESCRIPTION_LENGTH = 10;
        private static final String ELLIPSIS = "...";

        public static String getMovieView(
                final BabylonMovie babylonMovie,
                final String badge,
                final Integer limit
        ) {
            final String template = TEMPLATE
                    .replace(BADGE_PLACEHOLDER, (badge == null) || badge.isEmpty() ? "" : badge + " ")
                    .replace(URL_PLACEHOLDER, babylonMovie.url())
                    .replace(TITLE_PLACEHOLDER, babylonMovie.title())
                    .replace(DATETIME_PLACEHOLDER, Optional.ofNullable(babylonMovie.datetime())
                            .map(datetime -> DATETIME_FORMAT_VIEW.format(Instant.ofEpochSecond(datetime)) + "\n")
                            .orElse(""))
                    .replace(LENGTH_PLACEHOLDER, babylonMovie.length())
                    .replace(TAGS_PLACEHOLDER, babylonMovie.tags()
                            .stream()
                            .map(tag -> tag
                                    .replaceAll("[^A-Za-z0-9_]", "_")
                                    .replaceAll("_{2,}", "_")
                                    .replaceAll("^_|_$", "")
                            )
                            .map(tag -> "#" + tag)
                            .collect(Collectors.joining(" ")))
                    .trim();
            final String description = babylonMovie.description() + "\n";
            final String view = template
                    .replace(DESCRIPTION_PLACEHOLDER, description);
            if ((limit == null) || (view.length() <= limit)) {
                return view;
            }

            log.debug("Too long view of a movie, limit {}: {}", limit, babylonMovie);
            final String[] nonDescriptionParts = template.split(DESCRIPTION_PLACEHOLDER);
            if (nonDescriptionParts.length != 2) {
                log.warn("Unexpected number of description placeholders ({}): {}", nonDescriptionParts.length, TEMPLATE);
                return null;
            }

            final int possibleDescriptionLength = limit - (nonDescriptionParts[0].length() + nonDescriptionParts[1].length());
            if (possibleDescriptionLength < 0) {
                return null;
            } else if (possibleDescriptionLength == 0) {
                return nonDescriptionParts[0] + nonDescriptionParts[1];
            } else if (possibleDescriptionLength <= MIN_DESCRIPTION_LENGTH) {
                if (description.length() <= MIN_DESCRIPTION_LENGTH) {
                    return nonDescriptionParts[0] + description + nonDescriptionParts[1];
                } else {
                    return nonDescriptionParts[0] + nonDescriptionParts[1];
                }
            } else if (description.length() <= possibleDescriptionLength) {
                return nonDescriptionParts[0] + description + nonDescriptionParts[1];
            } else {
                return nonDescriptionParts[0] + truncate(description, possibleDescriptionLength - 1) + "\n" + nonDescriptionParts[1];
            }
        }

        private static String truncate(final String s, final int limit) {
            if (s.length() <= limit) {
                return s;
            } else {
                return limit < ELLIPSIS.length()
                        ? ""
                        : s.substring(0, limit - ELLIPSIS.length()).trim() + ELLIPSIS;
            }
        }

    }

}
