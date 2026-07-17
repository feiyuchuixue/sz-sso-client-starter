package com.sz.ssoclient.clientaccess.inbound;

import java.time.Instant;

/** Idempotency boundary shared by SLO and business-message callbacks. */
public interface ClientInboundEventStore {

    ClientInboundEventStatus begin(String namespace, String eventId, Instant expiresAt);

    void complete(String namespace, String eventId, Instant expiresAt);

    void release(String namespace, String eventId);
}
