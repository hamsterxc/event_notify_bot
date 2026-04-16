package com.lonebytesoft.hamster.eventnotifybot;

import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Chat;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramApi;
import com.lonebytesoft.hamster.eventnotifybot.service.telegram.TelegramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class Test {

    private static final Logger log = LoggerFactory.getLogger(Test.class);

    static void main(String[] args) {
        final HttpService httpService = new HttpService(Duration.ofSeconds(1));
        final JsonMapper jsonMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        final TelegramApi telegramApi = new TelegramApi(
                httpService,
                jsonMapper,
                System.getenv("TELEGRAM_BOT_TOKEN")
        );
        final TelegramService telegramService = new TelegramService(telegramApi);

        telegramService.getUpdates(null)
                .stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Update::id))
                .ifPresent(update -> telegramService.sendMessage(
                        Optional.ofNullable(update.message()).map(Message::chat).map(Chat::id).orElse(null),
                        "https://babylonberlin.eu/images/regridart/500x350/images/stummfilme/metropoli_gold_web500.jpg",
                        Optional.ofNullable(update.message()).map(Message::text).map(text -> text  + " back at you!").orElse(null),
                        false
                ));
    }

}
