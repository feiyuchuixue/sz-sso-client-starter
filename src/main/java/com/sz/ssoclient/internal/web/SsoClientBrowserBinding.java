package com.sz.ssoclient.internal.web;

import com.sz.ssoclient.api.browser.SsoWebCodes;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** 不依赖 Servlet Session 粘性的 HttpOnly Browser Binding。 */
public final class SsoClientBrowserBinding {

    public static final String COOKIE_NAME = "SZ_SSO_BROWSER";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43,128}");

    private final Supplier<String> tokenSupplier;
    private final boolean secureCookies;

    public SsoClientBrowserBinding(Supplier<String> tokenSupplier, boolean secureCookies) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        this.secureCookies = secureCookies;
    }

    public String resolveOrCreate(
            HttpServletRequest request, HttpServletResponse response) {
        String current = find(request);
        if (current != null) {
            return current;
        }
        String created = tokenSupplier.get();
        if (!valid(created)) {
            throw new IllegalStateException(
                    "Browser Binding 必须包含至少 256 bit URL-safe 随机量");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(created, request, null).toString());
        return created;
    }

    public String require(HttpServletRequest request) {
        String current = find(request);
        if (current == null) {
            throw new SsoClientWebException(
                    SsoWebCodes.LOGIN_BROWSER_BINDING_FAILURE,
                    403,
                    "Browser Binding 不存在或不匹配");
        }
        return current;
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie("", request, Duration.ZERO).toString());
    }

    private ResponseCookie cookie(
            String value, HttpServletRequest request, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookies || request.isSecure())
                .sameSite("Lax")
                .path("/");
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    private static String find(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && valid(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static boolean valid(String value) {
        return value != null && TOKEN.matcher(value).matches();
    }
}
