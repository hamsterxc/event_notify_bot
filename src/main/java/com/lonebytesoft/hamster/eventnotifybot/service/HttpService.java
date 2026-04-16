package com.lonebytesoft.hamster.eventnotifybot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    private static final HttpResponse.BodyHandler<String> STRING_BODY_HANDLER = HttpResponse.BodyHandlers.ofString();

    private final HttpClient httpClient;

    public HttpService(
            final Duration timeout
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public Optional<HttpResponse<String>> fetch(final HttpRequest request) {
        final HttpLogger logger = new HttpLogger(log, request, true);
        logger.log(request);
        try {
            final HttpResponse<String> response = httpClient.send(request, STRING_BODY_HANDLER);
            logger.log(response);
            return Optional.of(response);
        } catch (IOException | InterruptedException e) {
            logger.log(e);
            return Optional.empty();
        }
    }

    public static boolean isSuccess(final HttpResponse<?> response) {
        return response.statusCode() / 100 == 2;
    }

    private static class HttpLogger {

        private final Logger logger;
        private final String tag;
        private boolean isBodyReadSafe;

        public HttpLogger(
                final Logger logger,
                final String tag,
                final boolean isBodyReadSafe
        ) {
            this.logger = logger;
            this.tag = tag;
            this.isBodyReadSafe = isBodyReadSafe;
        }

        public HttpLogger(
                final Logger logger,
                final HttpRequest request,
                final boolean isBodyReadSafe
        ) {
            this(
                    logger,
                    "%s %s".formatted(request.method(), request.uri()),
                    isBodyReadSafe
            );
        }

        public void log(final HttpRequest request) {
            logger.debug("Fetching {}", tag);
            logger.trace("Request {}: headers {}, content-length {}",
                    tag,
                    request.headers().map(),
                    request.bodyPublisher()
                            .map(HttpRequest.BodyPublisher::contentLength)
                            .orElse(0L));
        }

        public void log(final HttpResponse<?> response) {
            logger.debug("Fetched {}: HTTP {}", tag, response.statusCode());
            logger.trace("Response {}: HTTP {}, headers {}{}",
                    tag,
                    response.statusCode(),
                    response.headers().map(),
                    isBodyReadSafe ? "\n" + response.body() : "");
        }

        public void log(final Throwable e) {
            logger.error("Failed fetching {}", tag, e);
        }

    }

}
