package com.sz.ssoclient.clientaccess.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** JDK HTTP transport that never follows redirects for signed service calls. */
public class JdkClientAccessHttpTransport implements ClientAccessHttpTransport {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JdkClientAccessHttpTransport(Duration connectTimeout, Duration requestTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), requestTimeout);
    }

    JdkClientAccessHttpTransport(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    @Override
    public ClientAccessHttpResponse exchange(ClientAccessHttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(requestTimeout)
                .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        request.headers().forEach(builder::header);
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new ClientAccessHttpResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClientAccessTransportException("CAP request was interrupted", exception);
        } catch (IOException exception) {
            throw new ClientAccessTransportException("CAP request failed before a response was received", exception);
        }
    }
}
