package com.sz.ssoclient.clientaccess.web;

import java.time.Instant;

/** Browser-safe authorization entry; no Client credential is exposed. */
public record ClientLoginTransactionCreated(String authorizationUrl, Instant expiresAt) {
}
