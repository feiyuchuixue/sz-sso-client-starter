package com.sz.ssoclient.clientaccess.http;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/** Secret-bearing Client backend registration used only for CAP service calls. */
public record ClientAccessRegistration(
        URI serverBaseUri,
        String clientFlag,
        byte[] secret) {

    public ClientAccessRegistration {
        Objects.requireNonNull(serverBaseUri, "serverBaseUri");
        Objects.requireNonNull(clientFlag, "clientFlag");
        Objects.requireNonNull(secret, "secret");
        if (!serverBaseUri.isAbsolute() || clientFlag.isBlank() || secret.length == 0) {
            throw new IllegalArgumentException("Client Access registration is incomplete");
        }
        secret = Arrays.copyOf(secret, secret.length);
    }

    @Override
    public byte[] secret() {
        return Arrays.copyOf(secret, secret.length);
    }
}
