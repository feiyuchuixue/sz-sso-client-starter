package com.sz.ssoclient.internal.login.transaction;

import java.time.Instant;
import java.util.Objects;

/** 只保存 callback 必需数据与敏感关联摘要的登录事务。 */
public record SsoLoginTransaction(
        String stateHash,
        String browserHash,
        String back,
        Instant createdAt,
        Instant expiresAt,
        SsoLoginTransactionStatus status) {

    public SsoLoginTransaction {
        requireText(stateHash, "stateHash");
        requireText(browserHash, "browserHash");
        requireText(back, "back");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt 必须晚于 createdAt");
        }
    }

    public SsoLoginTransaction exchanging() {
        if (status != SsoLoginTransactionStatus.CREATED) {
            throw new IllegalStateException("登录事务只能从 CREATED 进入 EXCHANGING");
        }
        return new SsoLoginTransaction(
                stateHash,
                browserHash,
                back,
                createdAt,
                expiresAt,
                SsoLoginTransactionStatus.EXCHANGING);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
