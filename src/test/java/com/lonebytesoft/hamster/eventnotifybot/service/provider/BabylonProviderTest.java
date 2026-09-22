package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.HttpServiceMock;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                                "idTitle", "metropolis-omeu-live-babylon-orchester-berlin",
                                "idDatetime", "2026-11-08 18:00:00",
                                "title", "Metropolis (OmeU) LIVE Babylon Orchester Berlin",
                                "description", "Metropolis (OmeU) LIVE begleitet vom Babylon Orchester Berlin Metropolis, D 1927, R: Fritz Lang mit…",
                                "datetime", "So, 08.11. 18:00",
                                "length", "180 min.",
                                "url", "https://babylonberlin.eu/orchester/1334-metropolis-live-babylon-orchester-berlin",
                                "imageUrl", "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/metropoli_gold_web500.jpg",
                                "tags", List.of("highlight", "stummfilm", "stummfilm-live", "Orchester")
                        ),
                        Map.of(
                                "idTitle", "chaplins-the-gold-rush-with-live-orchestra",
                                "idDatetime", "2026-11-29 18:00:00",
                                "title", "Chaplin's The Gold Rush with LIVE Orchestra",
                                "description", "Chaplin's The Gold Rush [Goldrausch] Begleitet vom Babylon Orchester Berlin The Gold Rush [Goldrausch] USA,…",
                                "datetime", "So, 29.11. 18:00",
                                "length", "86 min.",
                                "url", "https://babylonberlin.eu/orchester/4350-chaplin-s-the-gold-rush-with-live-orchestra",
                                "imageUrl", "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/goldrush_banner_web500.jpg",
                                "tags", List.of("highlight", "english-ov", "stummfilm", "stummfilm-live", "Orchester")
                        )
                ),
                jsonMapper.readValue(data.get(), DATA_TYPE)
        );
    }

}
