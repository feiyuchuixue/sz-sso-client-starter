package com.sz.ssoclient.clientaccess.web;

/** Client-backend login transaction lifecycle. */
public enum ClientLoginTransactionStatus {
    AUTHORIZING,
    CREATED,
    EXCHANGING,
    COMPLETED
}
