package com.lonebytesoft.hamster.eventnotifybot.test;

import com.lonebytesoft.hamster.eventnotifybot.service.HttpService;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HttpServiceMock extends HttpService {

    private static final String MOCK_KEY = "%s %s";

    private final Map<String, HttpResponse<String>> responseMocks = new HashMap<>();

    public HttpServiceMock() {
        super(Duration.ofSeconds(1));
    }

    @Override
    public Optional<HttpResponse<String>> fetch(HttpRequest request) {
        final String key = MOCK_KEY.formatted(request.method(), request.uri());
        return Optional.ofNullable(responseMocks.get(key));
    }

    public void mock(
            final String requestMethod,
            final String requestUri,
            final int responseCode,
            final String responseBody
    ) {
        responseMocks.put(
                MOCK_KEY.formatted(requestMethod, requestUri),
                new HttpResponseMock(
                        HttpRequest.newBuilder()
                                .method(requestMethod, HttpRequest.BodyPublishers.noBody())
                                .uri(URI.create(requestUri))
                                .build(),
                        responseCode,
                        responseBody
                )
        );
    }

    private record HttpResponseMock(
            HttpRequest request,
            int statusCode,
            String body
    ) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (_, _) -> false);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

    }

}
