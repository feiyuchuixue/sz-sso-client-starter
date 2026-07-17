package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.pojo.SsoLoginResult;

import java.time.Instant;

/** Atomic persistence boundary for browser login transactions. */
public interface ClientLoginTransactionStore {

    void create(ClientLoginTransaction transaction, int maxPendingPerBrowser);

    ClientLoginTransaction find(String stateHash);

    ClientLoginTransaction beginExchange(String stateHash, String browserSessionHash, Instant now);

    void markAuthorized(String stateHash, String browserSessionHash, String authorizationRequestId,
            Instant expiresAt);

    void complete(String stateHash, String browserSessionHash, SsoLoginResult result);

    void resetToCreated(String stateHash, String browserSessionHash);

    void delete(String stateHash);
}
