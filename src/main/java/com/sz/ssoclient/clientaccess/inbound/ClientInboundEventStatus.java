package com.sz.ssoclient.clientaccess.inbound;

/** Atomic event-consumption state for CAP callbacks. */
public enum ClientInboundEventStatus {
    ACQUIRED,
    IN_PROGRESS,
    COMPLETED
}
