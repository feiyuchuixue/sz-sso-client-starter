package com.sz.ssoclient.clientaccess.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Starter-owned browser binding independent from servlet session affinity. */
public final class ClientBrowserBinding {

    public static final String COOKIE_NAME = "SZ_SSO_BROWSER";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43,128}");

    private final Supplier<String> tokenSupplier;

    public ClientBrowserBinding(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
    }

    public String resolveOrCreate(HttpServletRequest request, HttpServletResponse response) {
        String current = find(request);
        if (current != null) {
            return current;
        }
        String created = tokenSupplier.get();
        if (!valid(created)) {
            throw new IllegalStateException("browser binding token must contain at least 256 bits of URL-safe entropy");
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, created)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return created;
    }

    public String require(HttpServletRequest request) {
        String current = find(request);
        if (current == null) {
            throw new ClientLoginTransactionException("CAP-3002", "Browser binding cookie is required");
        }
        return current;
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
