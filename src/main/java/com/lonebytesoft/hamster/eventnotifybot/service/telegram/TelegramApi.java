package com.lonebytesoft.hamster.eventnotifybot.service.telegram;

import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Message;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.Update;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.User;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.GetUpdatesRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.LinkPreviewOptions;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.SendMessageRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.SendPhotoRequest;
import com.lonebytesoft.hamster.eventnotifybot.model.telegram.TelegramResponse;
import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.lonebytesoft.hamster.eventnotifybot.service.HttpService.isSuccess;

public class TelegramApi {

    private static final Logger log = LoggerFactory.getLogger(TelegramApi.class);

    private static final String URL = "https://api.telegram.org";

    private final HttpService httpService;
    private final JsonMapper jsonMapper;
    private final String token;

    public TelegramApi(
            final HttpService httpService,
            final JsonMapper jsonMapper,
            final String token
    ) {
        this.httpService = httpService;
        this.jsonMapper = jsonMapper;
        this.token = token;
    }

    public Optional<User> getMe() {
        return this.<Void, User>call("GET", "getMe", null, new TypeReference<>(){})
                .map(TelegramResponse::result);
    }

    public Optional<List<Update>> getUpdates(
            final Long offset,
            final int batchSize,
            final Collection<String> updateTypes
    ) {
        final GetUpdatesRequest request = new GetUpdatesRequest(offset, batchSize, 0, updateTypes);
        return this.<GetUpdatesRequest, List<Update>>call("GET", "getUpdates", request, new TypeReference<>(){})
                .map(TelegramResponse::result);
    }

    public Optional<Message> sendMessage(
            final Long chatId,
            final String text,
            final String parseMode,
            final Boolean showLinkPreview
    ) {
        final LinkPreviewOptions linkPreviewOptions = new LinkPreviewOptions(!showLinkPreview);
        final SendMessageRequest request = new SendMessageRequest(chatId, text, parseMode, linkPreviewOptions);
        return this.<SendMessageRequest, Message>call("GET", "sendMessage", request, new TypeReference<>(){})
                .map(TelegramResponse::result);
    }

    public Optional<Message> sendPhoto(
            final Long chatId,
            final String photoUrl,
            final String text,
            final String parseMode,
            final Boolean showLinkPreview
    ) {
        final LinkPreviewOptions linkPreviewOptions = new LinkPreviewOptions(!showLinkPreview);
        final SendPhotoRequest request = new SendPhotoRequest(chatId, photoUrl, text, parseMode, linkPreviewOptions);
        return this.<SendPhotoRequest, Message>call("GET", "sendPhoto", request, new TypeReference<>(){})
                .map(TelegramResponse::result);
    }

    private <Request, Response> Optional<TelegramResponse<Response>> call(
            final String httpMethod,
            final String apiMethod,
            final Request body,
            final TypeReference<TelegramResponse<Response>> typeReference
    ) {
        final String endpoint = URL + "/bot" + token + "/" + apiMethod;
        final HttpRequest.BodyPublisher bodyPublisher = Optional.ofNullable(body)
                .map(jsonMapper::writeValueAsString)
                .map(HttpRequest.BodyPublishers::ofString)
                .orElseGet(HttpRequest.BodyPublishers::noBody);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .method(httpMethod, bodyPublisher)
                .header("Content-Type", "application/json")
                .build();

        final Optional<HttpResponse<String>> responseOptional = httpService.fetch(request);
        if (responseOptional.isEmpty()) {
            log.warn("Could not call Telegram method '{} {}'", httpMethod, apiMethod);
            return Optional.empty();
        }
        final HttpResponse<String> response = responseOptional.get();
        if (!isSuccess(response)) {
            log.warn("Unexpected Telegram method '{} {}' response code {}", httpMethod, apiMethod, response.statusCode());
            return Optional.empty();
        }

        final Optional<TelegramResponse<Response>> telegramResponse;
        try {
            telegramResponse = Optional.ofNullable(jsonMapper.readValue(response.body(), typeReference));
        } catch (JacksonException e) {
            log.warn("Could not parse Telegram method '{} {}' response", httpMethod, apiMethod, e);
            return Optional.empty();
        }
        if (!telegramResponse.map(TelegramResponse::ok).orElse(false)) {
            log.warn("Unsuccessful Telegram method '{} {}' response: {}",
                    httpMethod,
                    apiMethod,
                    telegramResponse.map(jsonMapper::writeValueAsString).orElse(null)
            );
            return Optional.empty();
        }

        return telegramResponse;
    }

}
