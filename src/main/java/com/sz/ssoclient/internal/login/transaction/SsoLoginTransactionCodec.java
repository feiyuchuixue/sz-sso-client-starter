package com.sz.ssoclient.internal.login.transaction;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** 使用固定版本、字段顺序和 JDK 类型的确定性事务 codec。 */
public final class SsoLoginTransactionCodec {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = "|";

    public String encode(SsoLoginTransaction transaction) {
        java.util.Objects.requireNonNull(transaction, "transaction");
        return String.join(
                SEPARATOR,
                VERSION,
                transaction.stateHash(),
                transaction.browserHash(),
                encodeText(transaction.back()),
                Long.toString(transaction.createdAt().toEpochMilli()),
                Long.toString(transaction.expiresAt().toEpochMilli()),
                transaction.status().name());
    }

    public SsoLoginTransaction decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("登录事务编码不能为空");
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 7 || !VERSION.equals(fields[0])) {
            throw new IllegalArgumentException("不支持的登录事务编码版本或字段数量");
        }
        try {
            return new SsoLoginTransaction(
                    fields[1],
                    fields[2],
                    decodeText(fields[3]),
                    Instant.ofEpochMilli(Long.parseLong(fields[4])),
                    Instant.ofEpochMilli(Long.parseLong(fields[5])),
                    SsoLoginTransactionStatus.valueOf(fields[6]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("登录事务编码内容非法", exception);
        }
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
