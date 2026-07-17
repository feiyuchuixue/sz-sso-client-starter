package com.sz.ssoclient.clientaccess.web;

import com.sz.ssoclient.pojo.SsoLoginResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-process transaction store. Production clusters should provide a shared atomic implementation.
 */
public class InMemoryClientLoginTransactionStore implements ClientLoginTransactionStore {

    private final ConcurrentHashMap<String, ClientLoginTransaction> transactions = new ConcurrentHashMap<>();

    @Override
    public synchronized void create(ClientLoginTransaction transaction, int maxPendingPerBrowser) {
        cleanupExpired(transaction.createdAt());
        long pending = transactions.values().stream()
                .filter(current -> same(current.browserSessionHash(), transaction.browserSessionHash()))
                .filter(current -> current.status() != ClientLoginTransactionStatus.COMPLETED)
                .count();
        if (pending >= maxPendingPerBrowser) {
            throw invalid("Too many pending login transactions for this browser session");
        }
        if (transactions.putIfAbsent(transaction.stateHash(), transaction) != null) {
            throw invalid("Login state collision");
        }
    }

    @Override
    public ClientLoginTransaction find(String stateHash) {
        return transactions.get(stateHash);
    }

    @Override
    public void markAuthorized(String stateHash, String browserSessionHash, String authorizationRequestId,
            Instant expiresAt) {
        AtomicReference<ClientLoginTransactionException> failure = new AtomicReference<>();
        transactions.compute(stateHash, (key, current) -> {
            if (current == null) {
                failure.set(invalid("Login transaction is missing"));
                return null;
            }
            if (!same(current.browserSessionHash(), browserSessionHash)
                    || current.status() != ClientLoginTransactionStatus.AUTHORIZING) {
                failure.set(invalid("Login transaction cannot be authorized"));
                return current;
            }
            return current.authorized(authorizationRequestId, expiresAt);
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    @Override
    public ClientLoginTransaction beginExchange(String stateHash, String browserSessionHash, Instant now) {
        AtomicReference<ClientLoginTransaction> result = new AtomicReference<>();
        AtomicReference<ClientLoginTransactionException> failure = new AtomicReference<>();
        transactions.compute(stateHash, (key, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                failure.set(invalid("Login transaction is missing or expired"));
                return null;
            }
            if (!same(current.browserSessionHash(), browserSessionHash)) {
                failure.set(invalid("Login transaction does not belong to this browser session"));
                return current;
            }
            if (current.status() == ClientLoginTransactionStatus.EXCHANGING) {
                failure.set(invalid("Login transaction is already being exchanged"));
                return current;
            }
            if (current.status() == ClientLoginTransactionStatus.COMPLETED) {
                result.set(current);
                return current;
            }
            if (current.status() != ClientLoginTransactionStatus.CREATED) {
                failure.set(invalid("Login transaction is not ready for exchange"));
                return current;
            }
            ClientLoginTransaction exchanging = current.withStatus(ClientLoginTransactionStatus.EXCHANGING);
            result.set(exchanging);
            return exchanging;
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        if (result.get() == null) {
            throw invalid("Login transaction is missing or expired");
        }
        return result.get();
    }

    @Override
    public void complete(String stateHash, String browserSessionHash, SsoLoginResult result) {
        transactions.compute(stateHash, (key, current) -> {
            if (current == null || !same(current.browserSessionHash(), browserSessionHash)
                    || current.status() != ClientLoginTransactionStatus.EXCHANGING) {
                throw invalid("Login transaction cannot be completed");
            }
            return current.completed(result);
        });
    }

    @Override
    public void resetToCreated(String stateHash, String browserSessionHash) {
        transactions.computeIfPresent(stateHash, (key, current) ->
                same(current.browserSessionHash(), browserSessionHash)
                        && current.status() == ClientLoginTransactionStatus.EXCHANGING
                        ? current.withStatus(ClientLoginTransactionStatus.CREATED) : current);
    }

    @Override
    public void delete(String stateHash) {
        transactions.remove(stateHash);
    }

    private void cleanupExpired(Instant now) {
        transactions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static ClientLoginTransactionException invalid(String message) {
        return new ClientLoginTransactionException("CAP-3002", message);
    }
}
