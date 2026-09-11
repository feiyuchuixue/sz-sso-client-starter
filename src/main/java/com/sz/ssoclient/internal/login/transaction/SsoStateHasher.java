package com.sz.ssoclient.internal.login.transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 登录 state 与 Browser 绑定值的单向 SHA-256 摘要工具。 */
public final class SsoStateHasher {

    private SsoStateHasher() {
    }

    public static String sha256(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("摘要输入不能为空");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
