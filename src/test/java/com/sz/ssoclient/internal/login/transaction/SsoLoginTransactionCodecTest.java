package com.sz.ssoclient.internal.login.transaction;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoLoginTransactionCodecTest {

    @Test
    void codecIsDeterministicVersionedAndRoundTripsOnlyFrozenFields() {
        SsoLoginTransaction transaction = new SsoLoginTransaction(
                "a".repeat(64),
                "b".repeat(64),
                "/dashboard?tab=1",
                Instant.parse("2026-08-04T00:00:00Z"),
                Instant.parse("2026-08-04T00:01:00Z"),
                SsoLoginTransactionStatus.CREATED);
        SsoLoginTransactionCodec codec = new SsoLoginTransactionCodec();

        String first = codec.encode(transaction);
        String second = codec.encode(transaction);

        assertEquals(first, second);
        assertTrue(first.startsWith("v1|"));
        assertEquals(transaction, codec.decode(first));
        assertFalse(first.contains("accessToken"));
        assertFalse(first.contains("completedResult"));
        assertEquals(Set.of(
                        "stateHash", "browserHash", "back", "createdAt", "expiresAt", "status"),
                Arrays.stream(SsoLoginTransaction.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void onlyCreatedAndExchangingStatusesExist() {
        assertEquals(List.of("CREATED", "EXCHANGING"),
                Arrays.stream(SsoLoginTransactionStatus.values()).map(Enum::name).toList());
    }

    @Test
    void safeBackAcceptsRelativeOrExplicitAllowlistAndRejectsAmbiguousUrls() {
        SsoSafeBackValidator validator = new SsoSafeBackValidator(
                Set.of("https://client.example.com/callback"));

        assertEquals("/dashboard?tab=1", validator.validate("/dashboard?tab=1"));
        assertEquals("https://client.example.com/callback",
                validator.validate("https://client.example.com/callback"));
        for (String candidate : List.of(
                "//evil.example/path",
                "https://evil.example/path",
                "/safe\\evil",
                "/safe\nheader",
                "javascript:alert(1)")) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(candidate), candidate);
        }
    }
}
