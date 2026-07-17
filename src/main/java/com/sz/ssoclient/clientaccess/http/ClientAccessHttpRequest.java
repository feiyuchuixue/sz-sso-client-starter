package com.sz.ssoclient.clientaccess.http;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** Exact bytes and signed headers passed to the HTTP transport. */
public record ClientAccessHttpRequest(String method, URI uri, Map<String, String> headers, byte[] body) {

    public ClientAccessHttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(uri, "uri");
        headers = Map.copyOf(headers);
        body = Arrays.copyOf(body, body.length);
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }
}
