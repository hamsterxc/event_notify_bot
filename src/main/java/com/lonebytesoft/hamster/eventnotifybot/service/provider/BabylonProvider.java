package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderDataChange;
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
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private final InstantSource instantSource;

    public BabylonProvider(
            final HttpService httpService,
            final JsonMapper jsonMapper,
            final InstantSource instantSource
    ) {
        this.httpService = httpService;
        this.jsonMapper = jsonMapper;
        this.instantSource = instantSource;
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

        return Optional.of(movies.isEmpty()
                ? ViewBuilder.getStateEmptyView()
                : ViewBuilder.getStateView(movies, limit, limitWithImage)
        );
    }

    @Override
    public Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage) {
        final Collection<BabylonMovie> baseMovies;
        final Collection<BabylonMovie> newMovies;
        try {
            baseMovies = new HashSet<>(jsonMapper.readValue(baseData, DATA_TYPE));
            newMovies = new HashSet<>(jsonMapper.readValue(newData, DATA_TYPE));
        } catch (JacksonException e) {
            log.warn("Could not parse movies: {} -> {}", baseData, newData);
            return Optional.empty();
        }

        final long now = instantSource.instant().getEpochSecond();
        final Predicate<BabylonMovie> movieInFuturePredicate = movie -> Optional.ofNullable(movie.datetime())
                .map(datetime -> datetime > now)
                .orElse(true);
        final Collector<BabylonMovie, ?, Map<String, List<BabylonMovie>>> movieTimeSortingCollector = Collectors.groupingBy(
                BabylonMovie::id,
                Collectors.collectingAndThen(
                        Collectors.toList(),
                        movies -> movies
                                .stream()
                                .sorted(Comparator.comparing(BabylonMovie::datetime))
                                .toList()
                )
        );
        final Map<String, List<BabylonMovie>> baseUnique = baseMovies
                .stream()
                .filter(movieInFuturePredicate)
                .filter(movie -> !newMovies.contains(movie))
                .collect(movieTimeSortingCollector);
        final Map<String, List<BabylonMovie>> newUnique = newMovies
                .stream()
                .filter(movieInFuturePredicate)
                .filter(movie -> !baseMovies.contains(movie))
                .collect(movieTimeSortingCollector);
        final List<ProviderDataChange<BabylonMovie>> changes = Stream.of(baseUnique, newUnique)
                .flatMap(unique -> unique.keySet().stream())
                .distinct()
                .flatMap(id -> {
                    final List<BabylonMovie> idBase = baseUnique.getOrDefault(id, List.of());
                    final List<BabylonMovie> idNew = newUnique.getOrDefault(id, List.of());
                    final int baseShift;
                    final int updatedSize;
                    final Collection<ProviderDataChange<BabylonMovie>> additionalChanges;
                    if (idBase.size() > idNew.size()) {
                        // if there are more in base than in new, the earliest extra base are considered deleted
                        baseShift = idBase.size() - idNew.size();
                        updatedSize = idNew.size();
                        additionalChanges = IntStream.range(0, baseShift)
                                .<ProviderDataChange<BabylonMovie>>mapToObj(index -> new ProviderDataChange.Delete<>(idBase.get(index)))
                                .toList();
                    } else {
                        // if there are more in new than in base, the latest extra new are considered created
                        baseShift = 0;
                        updatedSize = idBase.size();
                        additionalChanges = IntStream.range(0, idNew.size() - idBase.size())
                                .<ProviderDataChange<BabylonMovie>>mapToObj(index -> new ProviderDataChange.Create<>(idNew.get(updatedSize + index)))
                                .toList();
                    }
                    return Stream.of(
                                    additionalChanges.stream(),
                                    // everything else is a potential update
                                    IntStream.range(0, updatedSize)
                                            .<ProviderDataChange<BabylonMovie>>mapToObj(index -> {
                                                final BabylonMovie movieBase = idBase.get(baseShift + index);
                                                final BabylonMovie movieNew = idNew.get(index);
                                                // a change is an update only if time has changed
                                                return Objects.equals(movieBase.datetime(), movieNew.datetime())
                                                        ? null
                                                        : new ProviderDataChange.Update<>(movieBase, movieNew);
                                            })
                                            .filter(Objects::nonNull)
                            )
                            .flatMap(Function.identity());
                })
                .sorted(Comparator.comparing(change -> switch (change) {
                    case ProviderDataChange.Create<BabylonMovie> create -> create.newData().datetime();
                    case ProviderDataChange.Update<BabylonMovie> update -> update.newData().datetime();
                    case ProviderDataChange.Delete<BabylonMovie> delete -> delete.oldData().datetime();
                }))
                .toList();

        return changes.isEmpty()
                ? Optional.empty()
                : Optional.of(ViewBuilder.getDiffView(changes, limit, limitWithImage));
    }

    private static class ViewBuilder {

        private static final String DELIMITER = "\n\n";

        private static final String ERROR_VIEW = ResourceUtils.read(RESOURCE_FOLDER + "/state_error.html").trim();

        private static final String NO_MOVIES_VIEW = ResourceUtils.read(RESOURCE_FOLDER + "/state_no_movies.html").trim();

        private static final String FOOTER_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer.html").trim();
        private static final String FOOTER_CONTENT_PLACEHOLDER = "%CONTENT%";
        private static final String FOOTER_DELIMITER = " ";

        private static final String FOOTER_STATE_MORE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_state_more.html").trim();
        private static final String FOOTER_STATE_TOTAL_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_state_total.html").trim();
        private static final String FOOTER_DIFF_MORE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_diff_more.html").trim();
        private static final String FOOTER_DIFF_TOTAL_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_diff_total.html").trim();
        private static final String FOOTER_COUNT_PLACEHOLDER = "%COUNT%";

        private static final String FULL_LIST_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/footer_full_list.html").trim();
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

        public static ProviderView getStateEmptyView() {
            return STATE_EMPTY_VIEW;
        }

        public static ProviderView getStateView(
                final List<BabylonMovie> movies,
                final Integer limit,
                final Integer limitWithImage
        ) {
            final int size = movies.size();
            final int reserved = getViewText(DELIMITER, getFooterElements(FOOTER_STATE_MORE_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, size)).length() - DELIMITER.length();
            final ProviderViewBuilder viewBuilder = new ProviderViewBuilder(
                    DELIMITER,
                    limit == null ? null : limit - reserved,
                    limitWithImage == null ? null : limitWithImage - reserved
            );
            int leftover = size;
            for (; leftover > 0; leftover--) {
                final BabylonMovie movie = movies.get(size - leftover);
                final Function<Integer, String> limitedView = currentLimit -> MovieViewBuilder.buildView(movie, currentLimit);
                final String imageUrl = movie.imageUrl();
                if (!viewBuilder.append(limitedView, imageUrl)) {
                    break;
                }
            }

            final ProviderView bodyView = viewBuilder.build();
            return new ProviderView(
                    bodyView.imageUrl(),
                    getViewText(bodyView.textHtml(), leftover == size
                            ? getFooterElements(FOOTER_STATE_TOTAL_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, leftover)
                            : getFooterElements(FOOTER_STATE_MORE_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, leftover))
            );
        }

        public static ProviderView getDiffView(
                final List<ProviderDataChange<BabylonMovie>> changes,
                final Integer limit,
                final Integer limitWithImage
        ) {
            final int size = changes.size();
            final int reserved = getViewText(DELIMITER, getFooterElements(FOOTER_DIFF_MORE_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, size)).length() - DELIMITER.length();
            final ProviderViewBuilder viewBuilder = new ProviderViewBuilder(
                    DELIMITER,
                    limit == null ? null : limit - reserved,
                    limitWithImage == null ? null : limitWithImage - reserved
            );
            int leftover = size;
            for (; leftover > 0; leftover--) {
                final ProviderDataChange<BabylonMovie> change = changes.get(size - leftover);
                final Function<Integer, String> limitedView = switch (change) {
                    case ProviderDataChange.Create<BabylonMovie> create -> currentLimit -> MovieViewBuilder.buildCreateView(create.newData(), currentLimit);
                    case ProviderDataChange.Update<BabylonMovie> update -> currentLimit -> MovieViewBuilder.buildUpdateView(update.oldData(), update.newData(), currentLimit);
                    case ProviderDataChange.Delete<BabylonMovie> delete -> currentLimit -> MovieViewBuilder.buildDeleteView(delete.oldData(), currentLimit);
                };
                final String imageUrl = switch (change) {
                    case ProviderDataChange.Create<BabylonMovie> create -> create.newData().imageUrl();
                    case ProviderDataChange.Update<BabylonMovie> update -> update.newData().imageUrl();
                    case ProviderDataChange.Delete<BabylonMovie> _ -> null; // image may be unavailable for a deleted movie
                };
                if (!viewBuilder.append(limitedView, imageUrl)) {
                    break;
                }
            }

            final ProviderView bodyView = viewBuilder.build();
            return new ProviderView(
                    bodyView.imageUrl(),
                    getViewText(bodyView.textHtml(), leftover == size
                            ? getFooterElements(FOOTER_DIFF_TOTAL_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, leftover)
                            : getFooterElements(FOOTER_DIFF_MORE_TEMPLATE, FOOTER_COUNT_PLACEHOLDER, leftover))
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
        private static final String TEMPLATE_CREATE = ResourceUtils.read(RESOURCE_FOLDER + "/movie_create.html");
        private static final String TEMPLATE_UPDATE = ResourceUtils.read(RESOURCE_FOLDER + "/movie_update.html");
        private static final String TEMPLATE_DELETE = ResourceUtils.read(RESOURCE_FOLDER + "/movie_delete.html");

        private static final String URL_PLACEHOLDER = "%URL%";
        private static final String TITLE_PLACEHOLDER = "%TITLE%";
        private static final String DESCRIPTION_PLACEHOLDER = "%DESCRIPTION_WITH_LF%";
        private static final String DATETIME_PLACEHOLDER = "%DATETIME%";
        private static final String DATETIME_OLD_PLACEHOLDER = "%DATETIME_OLD%";
        private static final String DATETIME_NEW_PLACEHOLDER = "%DATETIME_NEW%";
        private static final String LENGTH_PLACEHOLDER = "%LENGTH%";
        private static final String TAGS_PLACEHOLDER = "%TAGS%";

        private static final String DATETIME_UNKNOWN_VIEW = ResourceUtils.read(RESOURCE_FOLDER + "/datetime_unknown.html");
        private static final DateTimeFormatter DATETIME_FORMAT_VIEW = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy, HH:mm").withZone(DATETIME_ZONE);

        private static final int MIN_DESCRIPTION_LENGTH = 10;
        private static final String ELLIPSIS = "...";

        public static String buildView(
                final BabylonMovie babylonMovie,
                final Integer limit
        ) {
            final String template = TEMPLATE
                    .replace(URL_PLACEHOLDER, babylonMovie.url())
                    .replace(TITLE_PLACEHOLDER, babylonMovie.title())
                    .replace(DATETIME_PLACEHOLDER, buildDatetime(babylonMovie.datetime()))
                    .replace(LENGTH_PLACEHOLDER, babylonMovie.length())
                    .replace(TAGS_PLACEHOLDER, buildTags(babylonMovie.tags()))
                    .trim();
            return withDescription(template, babylonMovie.description(), limit);
        }

        public static String buildCreateView(
                final BabylonMovie babylonMovie,
                final Integer limit
        ) {
            final String template = TEMPLATE_CREATE
                    .replace(URL_PLACEHOLDER, babylonMovie.url())
                    .replace(TITLE_PLACEHOLDER, babylonMovie.title())
                    .replace(DATETIME_PLACEHOLDER, buildDatetime(babylonMovie.datetime()))
                    .replace(LENGTH_PLACEHOLDER, babylonMovie.length())
                    .replace(TAGS_PLACEHOLDER, buildTags(babylonMovie.tags()))
                    .trim();
            return withDescription(template, babylonMovie.description(), limit);
        }

        public static String buildUpdateView(
                final BabylonMovie babylonMovieOld,
                final BabylonMovie babylonMovieNew,
                final Integer limit
        ) {
            final String template = TEMPLATE_UPDATE
                    .replace(URL_PLACEHOLDER, babylonMovieNew.url())
                    .replace(TITLE_PLACEHOLDER, babylonMovieNew.title())
                    .replace(DATETIME_OLD_PLACEHOLDER, buildDatetime(babylonMovieOld.datetime()))
                    .replace(DATETIME_NEW_PLACEHOLDER, buildDatetime(babylonMovieNew.datetime()))
                    .replace(LENGTH_PLACEHOLDER, babylonMovieNew.length())
                    .replace(TAGS_PLACEHOLDER, buildTags(babylonMovieNew.tags()))
                    .trim();
            return withDescription(template, babylonMovieNew.description(), limit);
        }

        public static String buildDeleteView(
                final BabylonMovie babylonMovie,
                final Integer limit
        ) {
            final String template = TEMPLATE_DELETE
                    .replace(TITLE_PLACEHOLDER, babylonMovie.title())
                    .replace(DATETIME_PLACEHOLDER, buildDatetime(babylonMovie.datetime()))
                    .replace(LENGTH_PLACEHOLDER, babylonMovie.length())
                    .replace(TAGS_PLACEHOLDER, buildTags(babylonMovie.tags()))
                    .trim();
            return withDescription(template, babylonMovie.description(), limit);
        }

        private static String buildDatetime(final Long datetime) {
            return Optional.ofNullable(datetime)
                    .map(time -> DATETIME_FORMAT_VIEW.format(Instant.ofEpochSecond(time)))
                    .orElse(DATETIME_UNKNOWN_VIEW);
        }

        private static String buildTags(final List<String> tags) {
            return tags
                    .stream()
                    .filter(tag -> (tag != null) && !tag.isEmpty())
                    .map(tag -> "#" + tag
                            .replaceAll("[^A-Za-z0-9_]", "_")
                            .replaceAll("_{2,}", "_")
                            .replaceAll("^_|_$", ""))
                    .collect(Collectors.joining(" "));
        }

        private static String withDescription(
                final String template,
                final String movieDescription,
                final Integer limit
        ) {
            final String description = movieDescription + "\n";
            final String view = template
                    .replace(DESCRIPTION_PLACEHOLDER, description);
            if ((limit == null) || (view.length() <= limit)) {
                return view;
            }

            final String[] nonDescriptionParts = template.split(DESCRIPTION_PLACEHOLDER);
            if (nonDescriptionParts.length != 2) {
                log.warn("Unexpected number of description placeholders ({}): {}", nonDescriptionParts.length, TEMPLATE);
                return truncate(template, limit);
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
