package com.lonebytesoft.hamster.eventnotifybot;

import com.lonebytesoft.hamster.eventnotifybot.model.provider.babylon.BabylonMovie;
import com.lonebytesoft.hamster.eventnotifybot.model.provider.mzgb.MzgbGame;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.BabylonProvider;
import com.lonebytesoft.hamster.eventnotifybot.service.provider.MzgbProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

public class Test {

    private static final Logger log = LoggerFactory.getLogger(Test.class);

    static void main(String[] args) {
        final HttpService httpService = new HttpService(Duration.ofSeconds(1));
        final JsonMapper jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        final List<MzgbGame> mzgbGames = new MzgbProvider(httpService, jsonMapper).get();
        log.info("{}", mzgbGames);

        final List<BabylonMovie> babylonMovies = new BabylonProvider(httpService).get();
        log.info("{}", babylonMovies);
    }

}
