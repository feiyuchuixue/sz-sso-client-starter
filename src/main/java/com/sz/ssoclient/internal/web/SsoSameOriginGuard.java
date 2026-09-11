package com.sz.ssoclient.internal.web;

import com.sz.ssoclient.api.browser.SsoWebCodes;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** 对 Browser 写请求执行可信外部 Origin/Referer 同源检查。 */
public final class SsoSameOriginGuard {

    private final Origin trustedOrigin;
    private final boolean allowMissingSource;

    public SsoSameOriginGuard(URI trustedOrigin, boolean allowMissingSource) {
        this.trustedOrigin = Origin.from(Objects.requireNonNull(trustedOrigin, "trustedOrigin"));
        this.allowMissingSource = allowMissingSource;
    }

    public void verify(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        String origin = trimToNull(request.getHeader("Origin"));
        if (origin != null) {
            requireTrusted(origin);
            return;
        }
        String referer = trimToNull(request.getHeader("Referer"));
        if (referer != null) {
            requireTrusted(referer);
            return;
        }
        String fetchSite = trimToNull(request.getHeader("Sec-Fetch-Site"));
        if (allowMissingSource && (fetchSite == null
                || "same-origin".equalsIgnoreCase(fetchSite)
                || "none".equalsIgnoreCase(fetchSite))) {
            return;
        }
        throw invalid();
    }

    private void requireTrusted(String source) {
        try {
            if (!trustedOrigin.equals(Origin.from(new URI(source)))) {
                throw invalid();
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static SsoClientWebException invalid() {
        return new SsoClientWebException(
                SsoWebCodes.REQUEST_ORIGIN_INVALID,
                403,
                "请求来源不是受信同源页面");
    }

    private record Origin(String scheme, String host, int port) {

        private static Origin from(URI uri) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("trusted origin must be an HTTP origin");
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }
            return new Origin(
                    scheme.toLowerCase(Locale.ROOT),
                    host.toLowerCase(Locale.ROOT),
                    port);
        }
    }
}
