package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.ProviderView;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon.BabylonMovie;
import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.HttpServiceMock;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
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
    private final Provider provider = new BabylonProvider(httpService, jsonMapper, InstantSource.fixed(Instant.parse("2026-01-01T00:00:00Z")));

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
                descriptiondescriptionde...
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
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
                descriptiondescriptionde...
                
                <i>Thu, 01 Jan 2026, 01:00
                Length: length</i>
                
                #tag1 #tag2
                
                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
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
                <i>1 movie(s) in total. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
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
                null,
                """
                <i>1 movie(s) in total. See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
        assertTrue(stateView.get().textHtml().length() <= limit);
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
                null, // both have no image
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
                null, // both have no image
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

        final Optional<ProviderView> stateView = provider.getStateView(data, 0, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url second", // image of the second one, the first doesn't have any
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

        final Optional<ProviderView> stateView = provider.getStateView(data, 0, null);

        assertEquals(Optional.of(new ProviderView(
                null, // both have images but different
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
    public void getStateView_twoMoviesWithSameImage() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-01T00:00:00Z").getEpochSecond(),
                        "title first",
                        "description first",
                        "length first",
                        "url first",
                        "image-url",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        null,
                        Instant.parse("2026-01-02T00:00:00Z").getEpochSecond(),
                        "title second",
                        "description second",
                        "length second",
                        "url second",
                        "image-url",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getStateView(data, 0, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url", // both have the same image
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
                null, // both have no image
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
        final Optional<ProviderView> stateView = provider.getStateView(data, 0, limit);

        assertEquals(Optional.of(new ProviderView(
                "image-url first", // image of the first one, the second is cut completely
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
                null, // only the second has an image, but it is cut
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
        final Optional<ProviderView> stateView = provider.getStateView(data, 0, limit);

        assertEquals(Optional.of(new ProviderView(
                "image-url first", // image of the first one, the second is cut completely
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
    public void getDiffView_oldViewInvalid_noView() {
        final String dataOld = "{}";
        final String dataNew = jsonMapper.writeValueAsString(List.of());

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.empty(), stateView);
    }

    @Test
    public void getDiffView_newViewInvalid_noView() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = "{}";

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.empty(), stateView);
    }

    @Test
    public void getDiffView_oneMovieNoDiff_noView() {
        final String data = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(data, data, null, null);

        assertEquals(Optional.empty(), stateView);
    }

    @Test
    public void getDiffView_oneMovieWithoutImageCreated_diffWithoutImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        null,
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url">title</a></b>
                description

                <i>Thu, 01 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieWithImageCreated_diffWithImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url">title</a></b>
                description

                <i>Thu, 01 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesWithoutImageCreated_diffWithoutImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title-first",
                        "description-first",
                        "length-first",
                        "url-first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title-second",
                        "description-second",
                        "length-second",
                        "url-second",
                        null,
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url-first">title-first</a></b>
                description-first

                <i>Thu, 01 Jan 2026, 13:00
                Length: length-first</i>

                #tag11 #tag12

                &#x1f195; <i>New movie!</i>
                <b><a href="url-second">title-second</a></b>
                description-second

                <i>Fri, 02 Jan 2026, 13:00
                Length: length-second</i>

                #tag21 #tag22

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesOneWithImageCreated_diffWithImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title-first",
                        "description-first",
                        "length-first",
                        "url-first",
                        null,
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title-second",
                        "description-second",
                        "length-second",
                        "url-second",
                        "image-url-second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url-second",
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url-first">title-first</a></b>
                description-first

                <i>Thu, 01 Jan 2026, 13:00
                Length: length-first</i>

                #tag11 #tag12

                &#x1f195; <i>New movie!</i>
                <b><a href="url-second">title-second</a></b>
                description-second

                <i>Fri, 02 Jan 2026, 13:00
                Length: length-second</i>

                #tag21 #tag22

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesWithImagesCreated_diffWithoutImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title-first",
                        "description-first",
                        "length-first",
                        "url-first",
                        "image-url-first",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title-second",
                        "description-second",
                        "length-second",
                        "url-second",
                        "image-url-second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url-first">title-first</a></b>
                description-first

                <i>Thu, 01 Jan 2026, 13:00
                Length: length-first</i>

                #tag11 #tag12

                &#x1f195; <i>New movie!</i>
                <b><a href="url-second">title-second</a></b>
                description-second

                <i>Fri, 02 Jan 2026, 13:00
                Length: length-second</i>

                #tag21 #tag22

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesWithImagesCreatedOneOutdated_diffWithImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2025-01-01T12:00:00Z").getEpochSecond(),
                        "title-first",
                        "description-first",
                        "length-first",
                        "url-first",
                        "image-url-first",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title-second",
                        "description-second",
                        "length-second",
                        "url-second",
                        "image-url-second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url-second",
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url-second">title-second</a></b>
                description-second

                <i>Fri, 02 Jan 2026, 13:00
                Length: length-second</i>

                #tag21 #tag22

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesWithSameImageCreated_diffWithImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of());
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title-first",
                        "description-first",
                        "length-first",
                        "url-first",
                        "image-url-second",
                        List.of("tag11", "tag12")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title-second",
                        "description-second",
                        "length-second",
                        "url-second",
                        "image-url-second",
                        List.of("tag21", "tag22")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url-second",
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url-first">title-first</a></b>
                description-first

                <i>Thu, 01 Jan 2026, 13:00
                Length: length-first</i>

                #tag11 #tag12

                &#x1f195; <i>New movie!</i>
                <b><a href="url-second">title-second</a></b>
                description-second

                <i>Fri, 02 Jan 2026, 13:00
                Length: length-second</i>

                #tag21 #tag22

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieTimeUpdated() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x270d; <i>Movie rescheduled</i>
                <b><a href="url">title</a></b>
                description

                <i><s>Thu, 01 Jan 2026, 13:00</s> -> Thu, 01 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieUpdatedSameTime_noDiff() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title-new",
                        "description-new",
                        "length-new",
                        "url-new",
                        "image-url-new",
                        List.of("tag11", "tag21")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.empty(), stateView);
    }

    @Test
    public void getDiffView_oneMovieTimeUpdatedBothInPast_noDiff() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2025-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2025-01-01T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.empty(), stateView);
    }

    @Test
    public void getDiffView_oneMovieTimeUpdatedIntoPast_diffWithDeleted() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2025-01-01T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x274c; <i>Movie cancelled</i>
                <b>title</b>
                description

                <i>Thu, 01 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieTimeUpdatedIntoFuture_diffWithCreated() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2025-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x1f195; <i>New movie!</i>
                <b><a href="url">title</a></b>
                description

                <i>Thu, 01 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieWithoutImageDeleted_diffWithoutImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        null,
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of());

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x274c; <i>Movie cancelled</i>
                <b>title</b>
                description

                <i>Thu, 01 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_oneMovieWithImageDeleted_diffWithImage() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of());

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                null,
                """
                &#x274c; <i>Movie cancelled</i>
                <b>title</b>
                description

                <i>Thu, 01 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesSameIdOneRescheduled_diffOneUpdate() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x270d; <i>Movie rescheduled</i>
                <b><a href="url">title</a></b>
                description

                <i><s>Fri, 02 Jan 2026, 13:00</s> -> Fri, 02 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesSameIdOneRescheduledWithDifferentId_diffOneCreateOneDelete() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "second",
                        Instant.parse("2026-01-02T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x274c; <i>Movie cancelled</i>
                <b>title</b>
                description

                <i>Fri, 02 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                &#x1f195; <i>New movie!</i>
                <b><a href="url">title</a></b>
                description

                <i>Fri, 02 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_twoMoviesSameIdIntoThreeMovies_diffOneCreateOneUpdate() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-03T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x270d; <i>Movie rescheduled</i>
                <b><a href="url">title</a></b>
                description

                <i><s>Fri, 02 Jan 2026, 13:00</s> -> Fri, 02 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                &#x1f195; <i>New movie!</i>
                <b><a href="url">title</a></b>
                description

                <i>Sat, 03 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

    @Test
    public void getDiffView_threeMoviesSameIdIntoTwoMovies_diffOneDeleteOneUpdate() {
        final String dataOld = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-02T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-03T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));
        final String dataNew = jsonMapper.writeValueAsString(List.of(
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-01T12:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                ),
                new BabylonMovie(
                        "first",
                        Instant.parse("2026-01-03T13:00:00Z").getEpochSecond(),
                        "title",
                        "description",
                        "length",
                        "url",
                        "image-url",
                        List.of("tag1", "tag2")
                )
        ));

        final Optional<ProviderView> stateView = provider.getDiffView(dataOld, dataNew, null, null);

        assertEquals(Optional.of(new ProviderView(
                "image-url",
                """
                &#x274c; <i>Movie cancelled</i>
                <b>title</b>
                description

                <i>Fri, 02 Jan 2026, 13:00
                Length: length</i>

                #tag1 #tag2

                &#x270d; <i>Movie rescheduled</i>
                <b><a href="url">title</a></b>
                description

                <i><s>Sat, 03 Jan 2026, 13:00</s> -> Sat, 03 Jan 2026, 14:00
                Length: length</i>

                #tag1 #tag2

                <i>See the full schedule at <a href="https://babylonberlin.eu/programm">Babylon</a>.</i>
                """.trim().stripIndent()
        )), stateView);
    }

}
