package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon.BabylonMovie;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.lonebytesoft.hamster.eventnotifybot.service.HttpService.isSuccess;

public class BabylonProvider implements Provider<BabylonMovie> {

    private static final Logger log = LoggerFactory.getLogger(BabylonProvider.class);

    private static final String URL = "https://babylonberlin.eu";

    private final HttpService httpService;

    public BabylonProvider(
            final HttpService httpService
    ) {
        this.httpService = httpService;
    }

    @Override
    public List<BabylonMovie> get() {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL + "/programm"))
                .GET()
                .build();
        final Optional<HttpResponse<String>> responseOptional = httpService.fetch(request);
        if (responseOptional.isEmpty()) {
            log.warn("Could not fetch movies page, returning empty");
            return List.of();
        }
        final HttpResponse<String> response = responseOptional.get();
        if (!isSuccess(response)) {
            log.warn("Unexpected movies page response code {}, returning empty", response.statusCode());
            return List.of();
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
        return movies;
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

}
