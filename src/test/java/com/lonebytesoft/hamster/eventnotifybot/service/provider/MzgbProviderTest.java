package com.lonebytesoft.hamster.eventnotifybot.service.provider;

import com.lonebytesoft.hamster.eventnotifybot.service.ResourceUtils;
import com.lonebytesoft.hamster.eventnotifybot.test.mock.HttpServiceMock;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MzgbProviderTest {

    private static final String INIT_URL = "https://ber.mzgb.net";
    private static final String DATA_URL = "https://ber.mzgb.net/api/load-data?page=main&locale=ru";
    private static final TypeReference<List<Map<String, Object>>> DATA_TYPE = new TypeReference<>(){};

    private final HttpServiceMock httpService = new HttpServiceMock();
    private final JsonMapper jsonMapper = new JsonMapper();
    private final Provider provider = new MzgbProvider(httpService, jsonMapper);

    @Test
    public void getData_initialNoResponse_noData() {
        // no HTTP mock

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_initialNotFoundResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                404,
                "Not Found"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_initialServerErrorResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                500,
                "Internal Server Error"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_dataNoResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                200,
                ""
        );
        // no data HTTP mock

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_dataNotFoundResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                200,
                ""
        );
        httpService.mock(
                "POST",
                DATA_URL,
                404,
                "Not Found"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_dataServerErrorResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                200,
                ""
        );
        httpService.mock(
                "POST",
                DATA_URL,
                500,
                "Internal Server Error"
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_emptyResponse_noData() {
        httpService.mock(
                "GET",
                INIT_URL,
                200,
                ""
        );
        httpService.mock(
                "POST",
                DATA_URL,
                200,
                ""
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isEmpty());
    }

    @Test
    public void getData_success() {
        httpService.mock(
                "GET",
                INIT_URL,
                200,
                ""
        );
        httpService.mock(
                "POST",
                DATA_URL,
                200,
                ResourceUtils.read("mzgb.json")
        );

        final Optional<String> data = provider.getData();

        assertTrue(data.isPresent());
        assertEquals(
                List.of(
                        // working around non-null arguments in Map.of()
                        new HashMap<String, Object>() {{
                            put("id", 1);
                            put("name", "Christmas party");
                            put("description", null);
                            put("imageUrl", "https://ber.mzgb.net/christmas-image");
                            put("dateTime", "25 Dec 2026, 19:00");
                            put("price", "11 EUR");
                            put("address", "christmas-club, christmas-address");
                        }},
                        Map.of(
                                "id", 2,
                                "name", "Silvester party",
                                "description", "Итоговая игра сезона, где за звание чемпиона борются команды, занявшие призовые места в сезоне или отличившиеся в рейтинге. Остальные команды могут быть приглашены сыграть вне зачета.",
                                "imageUrl", "https://ber.mzgb.net/silvester-image",
                                "dateTime", "31 Dec 2026, 23:59",
                                "price", "12 USD",
                                "address", "silvester-club, silvester-address"
                        )
                ),
                jsonMapper.readValue(data.get(), DATA_TYPE)
        );
    }

}
