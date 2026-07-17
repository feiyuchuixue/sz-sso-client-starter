package com.sz.ssoclient.clientaccess.web.dto;

import java.time.Instant;

/** Browser-safe Client-local session response; internal device and mapping identifiers stay private. */
public record ClientSessionWebResponse(boolean authenticated, Object user, Instant expireAt) {
}
