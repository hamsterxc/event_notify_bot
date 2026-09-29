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
import java.util.ArrayList;
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

    private static final String RESOURCE_FOLDER = "message/provider/babylon";

    private static final String MOVIE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/movie.html");
    private static final String MOVIE_BADGE_PLACEHOLDER = "%BADGE_WITH_SPACE%";
    private static final String MOVIE_URL_PLACEHOLDER = "%URL%";
    private static final String MOVIE_TITLE_PLACEHOLDER = "%TITLE%";
    private static final String MOVIE_DESCRIPTION_PLACEHOLDER = "%DESCRIPTION_WITH_LF%";
    private static final String MOVIE_DATETIME_PLACEHOLDER = "%DATETIME%";
    private static final String MOVIE_LENGTH_PLACEHOLDER = "%LENGTH%";
    private static final String MOVIE_TAGS_PLACEHOLDER = "%TAGS%";
    private static final int MIN_MOVIE_DESCRIPTION_LENGTH = 10;
    private static final String ELLIPSIS = "...";

    private static final String ERROR_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/error.html").trim();

    private static final String NO_MOVIES_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/no_movies.html").trim();

    private static final String MORE_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/more.html").trim();
    private static final String MORE_COUNT_PLACEHOLDER = "%COUNT%";

    private static final String FULL_LIST_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/full_list.html").trim();
    private static final String FULL_LIST_URL_PLACEHOLDER = "%URL%";

    private static final String VIEW_MOVIES_PLACEHOLDER = "%MOVIES%";
    private static final String VIEW_MORE_PLACEHOLDER = "%MORE_WITH_SPACE%";
    private static final String VIEW_FULL_LIST_PLACEHOLDER = "%FULL_LIST%";
    private static final String VIEW_TEMPLATE = ResourceUtils.read(RESOURCE_FOLDER + "/view.html")
            .replace(VIEW_FULL_LIST_PLACEHOLDER, FULL_LIST_TEMPLATE.replace(FULL_LIST_URL_PLACEHOLDER, URL + URL_SCHEDULE))
            .trim();

    private static final String ERROR_VIEW = VIEW_TEMPLATE
            .replace(VIEW_MOVIES_PLACEHOLDER, ERROR_TEMPLATE)
            .replace(VIEW_MORE_PLACEHOLDER, "");
    private static final String NO_MOVIES_VIEW = VIEW_TEMPLATE
            .replace(VIEW_MOVIES_PLACEHOLDER, NO_MOVIES_TEMPLATE)
            .replace(VIEW_MORE_PLACEHOLDER, "");

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
                    return new BabylonMovie(
                            getAttribute(element, "data-title").orElse(null),
                            getAttribute(element, "data-date").orElse(null),
                            getOwnText(linkElement).orElse(null),
                            getOwnText(element.selectFirst("> div.inner-mix > div.mix-introtext-outer > p.mix-introtext")).orElse(null),
                            getOwnText(element.selectFirst("> div.inner-mix > div.mix-extra > p.mix-date")).orElse(null),
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
            return Optional.of(new ProviderView(
                    null,
                    ERROR_VIEW
            ));
        }

        final int size = movies.size();
        if (size == 0) {
            return Optional.of(new ProviderView(
                    null,
                    NO_MOVIES_VIEW
            ));
        }

        Integer textLimit;
        if (size == 1) {
            textLimit = Optional.ofNullable(movies.getFirst().imageUrl() == null ? limit : limitWithImage)
                    .map(currentLimit -> {
                        final int extraLength = VIEW_TEMPLATE
                                .replace(VIEW_MOVIES_PLACEHOLDER, "")
                                .replace(VIEW_MORE_PLACEHOLDER, "")
                                .length();
                        return currentLimit - extraLength;
                    })
                    .orElse(null);
        } else {
            textLimit = Optional.ofNullable(limit)
                    .map(currentLimit -> {
                        final int extraLength = VIEW_TEMPLATE
                                .replace(VIEW_MOVIES_PLACEHOLDER, "")
                                .replace(VIEW_MORE_PLACEHOLDER, MORE_TEMPLATE.replace(MORE_COUNT_PLACEHOLDER, String.valueOf(size)) + " ")
                                .length();
                        return currentLimit - extraLength;
                    })
                    .orElse(null);
        }

        final List<String> movieViews = new ArrayList<>();
        int leftoverSize = size;

        final String firstView = getView(movies.getFirst(), null, textLimit);
        movieViews.add(firstView);
        leftoverSize--;
        if (textLimit != null) {
            textLimit -= firstView.length();
        }

        for (; leftoverSize > 0; leftoverSize--) {
            final BabylonMovie movie = movies.get(size - leftoverSize);
            final String movieView = getView(movie, null, null);
            if ((textLimit == null) || (movieView.length() + 2 <= textLimit)) {
                movieViews.add(movieView);
                leftoverSize--;
                if (textLimit != null) {
                    textLimit -= movieView.length() + 2;
                }
            } else {
                break;
            }
        }

        return Optional.of(new ProviderView(
                movieViews.size() == 1 ? movies.getFirst().imageUrl() : null,
                VIEW_TEMPLATE
                        .replace(VIEW_MOVIES_PLACEHOLDER, String.join("\n\n", movieViews))
                        .replace(VIEW_MORE_PLACEHOLDER, leftoverSize > 0
                                ? MORE_TEMPLATE.replace(MORE_COUNT_PLACEHOLDER, String.valueOf(leftoverSize)) + " "
                                : "")
        ));
    }

    @Override
    public Optional<ProviderView> getDiffView(String baseData, String newData, Integer limit, Integer limitWithImage) {
        // todo
        throw new UnsupportedOperationException();
    }

    private String getView(
            final BabylonMovie babylonMovie,
            final String badge,
            final Integer limit
    ) {
        final String template = MOVIE_TEMPLATE
                .replace(MOVIE_BADGE_PLACEHOLDER, (badge == null) || badge.isEmpty() ? "" : badge + " ")
                .replace(MOVIE_URL_PLACEHOLDER, babylonMovie.url())
                .replace(MOVIE_TITLE_PLACEHOLDER, babylonMovie.title())
                .replace(MOVIE_DATETIME_PLACEHOLDER, babylonMovie.datetime())
                .replace(MOVIE_LENGTH_PLACEHOLDER, babylonMovie.length())
                .replace(MOVIE_TAGS_PLACEHOLDER, babylonMovie.tags()
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
        if ((limit == null) || (template.length() - MOVIE_DESCRIPTION_PLACEHOLDER.length() + description.length() <= limit)) {
            return template
                    .replace(MOVIE_DESCRIPTION_PLACEHOLDER, description);
        }

        log.info("Too long view of a movie, limit {}: {}", limit, babylonMovie);
        final String beforeDescription = template.substring(0, template.indexOf(MOVIE_DESCRIPTION_PLACEHOLDER));
        final String afterDescription = template.substring(template.indexOf(MOVIE_DESCRIPTION_PLACEHOLDER) + MOVIE_DESCRIPTION_PLACEHOLDER.length());
        final int possibleDescriptionLength = limit - (beforeDescription.length() + afterDescription.length());
        if (possibleDescriptionLength < 0) {
            log.warn("Too long view of a movie even without description, limit {}: {}", limit, babylonMovie);
            return truncate(beforeDescription + afterDescription, limit);
        } else if (possibleDescriptionLength == 0) {
            return beforeDescription + afterDescription;
        } else if (possibleDescriptionLength <= MIN_MOVIE_DESCRIPTION_LENGTH) {
            if (description.length() <= MIN_MOVIE_DESCRIPTION_LENGTH) {
                return beforeDescription + description + afterDescription;
            } else {
                return beforeDescription + afterDescription;
            }
        } else if (description.length() <= possibleDescriptionLength) {
            return beforeDescription + description + afterDescription;
        } else {
            return beforeDescription + truncate(description, possibleDescriptionLength - 1) + "\n" + afterDescription;
        }
    }

    private String truncate(final String s, final int limit) {
        if (s.length() <= limit) {
            return s;
        } else {
            return limit < ELLIPSIS.length()
                    ? ""
                    : s.substring(0, limit - ELLIPSIS.length()).trim() + ELLIPSIS;
        }
    }

}
