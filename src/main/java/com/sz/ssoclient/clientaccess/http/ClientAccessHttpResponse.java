package com.sz.ssoclient.clientaccess.http;

import java.util.Arrays;

public record ClientAccessHttpResponse(int statusCode, byte[] body) {

    public ClientAccessHttpResponse {
        body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }
}
