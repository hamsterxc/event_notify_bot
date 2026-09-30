package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon.BabylonMovie;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.HttpServiceMock;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BabylonProviderTest {

    private static final String DATA_URL = "https://babylonberlin.eu/programm";
    private static final TypeReference<List<Map<String, Object>>> DATA_TYPE = new TypeReference<>(){};

    private final HttpServiceMock httpService = new HttpServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final Provider provider = new BabylonProvider(httpService, jsonMapper);

    @Test
    public void getData_noResponse_noData() {
        // no HTTP mock

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_notFoundResponse_noData() {
        httpService.mock(
                "GET",
                DATA_URL,
                404,
                "Not Found"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_serverErrorResponse_noData() {
        httpService.mock(
                "GET",
                DATA_URL,
                500,
                "Internal Server Error"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_emptyResponse_emptyData() {
        httpService.mock(
                "GET",
                DATA_URL,
                200,
                ""
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isPresent());
        assertEquals(
                List.of(),
                jsonMapper.readValue(data.get(), DATA_TYPE)
        );
    }

    @Test
    public void getData_success() {
        httpService.mock(
                "GET",
                DATA_URL,
                200,
                ResourceUtils.read("babylon.html")
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isPresent());
        assertEquals(
                List.of(
                        Map.of(
                                "id", "metropolis-omeu-live-babylon-orchester-berlin",
                                "datetime", 1794157200,
                                "title", "Metropolis (OmeU) LIVE Babylon Orchester Berlin",
                                "description", "Metropolis (OmeU) LIVE begleitet vom Babylon Orchester Berlin Metropolis, D 1927, R: Fritz Lang mit…",
                                "length", "180 min.",
                                "url", "https://babylonberlin.eu/orchester/1334-metropolis-live-babylon-orchester-berlin",
                                "imageUrl", "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/metropoli_gold_web500.jpg",
                                "tags", List.of("highlight", "stummfilm", "stummfilm-live", "Orchester")
                        ),
                        Map.of(
                                "id", "chaplins-the-gold-rush-with-live-orchestra",
                                "datetime", 1795971600,
                                "title", "Chaplin's The Gold Rush with LIVE Orchestra",
                                "description", "Chaplin's The Gold Rush [Goldrausch] Begleitet vom Babylon Orchester Berlin The Gold Rush [Goldrausch] USA,…",
                                "length", "86 min.",
                                "url", "https://babylonberlin.eu/orchester/4350-chaplin-s-the-gold-rush-with-live-orchestra",
                                "imageUrl", "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/goldrush_banner_web500.jpg",
                                "tags", List.of("highlight", "english-ov", "stummfilm", "stummfilm-live", "Orchester")
                        )
                ),
                jsonMapper.readValue(data.get(), DATA_TYPE)
        );
    }

    @Test
    public void getStateView_unparseableData() {
        final String data = "{}";

        final Optional<ProviderView> stateView = provider.getStateView(data, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <i>An error occurred while getting the movie list.</i>
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_emptyData() {
        final String data = jsonMapper.writeValueAsString(List.of());

        final Optional<ProviderView> stateView = provider.getStateView(data, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <i>No movies scheduled...</i>
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_oneMovieWithoutTags() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        null,
                        List.of()
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url">title</a></b>
                description
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_oneMovieWithoutImage() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        null,
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url">title</a></b>
                description
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_oneMovieWithImage() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, 0, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                <b><a href="url">title</a></b>
                description
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_oneMovieWithoutImageDescriptionOutsideLimit_descriptionCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title",
                        IntStream.range(0, 10).mapToObj(_ -> "description").collect(Collectors.joining()),
                        "length",
                        "url",
                        null,
                        List.of("tag1", "tag2")
                )
        ));

        final int limit = 220;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url">title</a></b>
                descriptiondescriptiondescriptiondes...
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertEquals(limit, stateView.get().textHtml().length());
    }

    @Test
    public void getStateView_oneMovieWithImageDescriptionOutsideLimit_descriptionCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title",
                        IntStream.range(0, 10).mapToObj(_ -> "description").collect(Collectors.joining()),
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final int limit = 220;
        final Optional<ProviderView> stateView = provider.getStateView(data, 0, limit);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                <b><a href="url">title</a></b>
                descriptiondescriptiondescriptiondes...
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertEquals(limit, stateView.get().textHtml().length());
    }

    @Test
    public void getStateView_oneMovieWithoutImageTitleOutsideLimit_viewCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        IntStream.range(0, 10).mapToObj(_ -> "title").collect(Collectors.joining()),
                        "description",
                        "length",
                        "url",
                        null,
                        List.of("tag1", "tag2")
                )
        ));

        final int limit = 220;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url">titletitletitletitletitletitletitletitletitletitle</a></b>
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #ta...
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertEquals(limit, stateView.get().textHtml().length());
    }

    @Test
    public void getStateView_oneMovieWithImageTitleOutsideLimit_viewCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        IntStream.range(0, 10).mapToObj(_ -> "title").collect(Collectors.joining()),
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final int limit = 220;
        final Optional<ProviderView> stateView = provider.getStateView(data, 0, limit);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                <b><a href="url">titletitletitletitletitletitletitletitletitletitle</a></b>
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #ta...
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertEquals(limit, stateView.get().textHtml().length());
    }

    @Test
    public void getStateView_twoMoviesOneWithoutOneWithExtraCharactersTags() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        null,
                        List.of()
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        null,
                        List.of(">tag-21!!", "<<tag-22?")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                <b><a href="url second">title second</a></b>
                description second

                <i>Fri, 02 Jan 2026, 01:00
                Length: length second</i>

                #tag_21 #tag_22
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_twoMoviesWithoutImage() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        null,
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <b><a href="url second">title second</a></b>
                description second

                <i>Fri, 02 Jan 2026, 01:00
                Length: length second</i>

                #tag21 #tag22
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_twoMoviesOneWithImage() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        "image-url second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <b><a href="url second">title second</a></b>
                description second

                <i>Fri, 02 Jan 2026, 01:00
                Length: length second</i>

                #tag21 #tag22
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_twoMoviesWithImages() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        "image-url first",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        "image-url second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, null, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <b><a href="url second">title second</a></b>
                description second

                <i>Fri, 02 Jan 2026, 01:00
                Length: length second</i>

                #tag21 #tag22
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getStateView_twoMoviesWithoutImagesOutsideLimit_secondCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        null,
                        List.of("tag21", "tag22")
                )
        ));

        final int limit = 300;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <i>And 1 more. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
    }

    @Test
    public void getStateView_twoMoviesOnlyFirstWithImageOutsideLimit_withImageSecondCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        "image-url first",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        null,
                        List.of("tag21", "tag22")
                )
        ));

        final int limit = 300;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                "image-url first",
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <i>And 1 more. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
    }

    @Test
    public void getStateView_twoMoviesOnlySecondWithImageOutsideLimit_noImageSecondCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        "image-url second",
                        List.of("tag21", "tag22")
                )
        ));

        final int limit = 300;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <i>And 1 more. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
    }

    @Test
    public void getStateView_twoMoviesWithImagesOutsideLimit_firstImageSecondCut() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        "image-url first",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        "image-url second",
                        List.of("tag21", "tag22")
                )
        ));

        final int limit = 300;
        final Optional<ProviderView> stateView = provider.getStateView(data, limit, 0);

        assertEquals(Optional.of(new ProviderView(
                "image-url first",
                """
                <b><a href="url first">title first</a></b>
                description first
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length first</i>
                
                #tag11 #tag12
                
                <i>And 1 more. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
    }

}
