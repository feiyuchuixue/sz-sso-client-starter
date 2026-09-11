package com.sz.ssoclient.internal.login.transaction;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Set;

/** 只允许安全相对路径或宿主显式批准的绝对回调。 */
public final class SsoSafeBackValidator {

    private final Set<String> allowedAbsoluteCallbacks;

    public SsoSafeBackValidator() {
        this(Set.of());
    }

    public SsoSafeBackValidator(Collection<String> allowedAbsoluteCallbacks) {
        this.allowedAbsoluteCallbacks = Set.copyOf(
                allowedAbsoluteCallbacks == null ? Set.of() : allowedAbsoluteCallbacks);
    }

    public String validate(String candidate) {
        String value = candidate == null || candidate.isBlank() ? "/" : candidate.trim();
        if (containsControl(value) || value.indexOf('\\') >= 0 || value.startsWith("//")) {
            throw new IllegalArgumentException("back 不是安全的 Client 回跳地址");
        }
        URI uri = parse(value);
        if (!uri.isAbsolute()) {
            if (!value.startsWith("/") || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("back 必须是安全相对路径或已允许回调");
            }
            return value;
        }
        if (!isHttp(uri) || uri.getUserInfo() != null || !allowedAbsoluteCallbacks.contains(value)) {
            throw new IllegalArgumentException("back 绝对地址未在 Client 允许回调中");
        }
        return value;
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("back 地址格式非法", exception);
        }
    }

    private static boolean isHttp(URI uri) {
        return uri.getHost() != null
                && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
