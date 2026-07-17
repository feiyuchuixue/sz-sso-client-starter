package com.sz.ssoclient.clientaccess.state;

import com.sz.ssocore.clientaccess.v1.ClientAccessSigner;

import java.nio.charset.StandardCharsets;

/** Stable, Redis Cluster compatible CAP state key builder. */
public final class ClientAccessStateKeys {

    private static final String ROOT = "sz:sso:cap:v1:";

    private ClientAccessStateKeys() {
    }

    public static String namespace(String clientFlag) {
        return ROOT + "{" + digest(required(clientFlag, "clientFlag")).substring(0, 16) + "}:";
    }

    public static String digest(String value) {
        return ClientAccessSigner.bodySha256(required(value, "state key").getBytes(StandardCharsets.UTF_8));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
