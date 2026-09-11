package com.sz.ssoclient.internal.bootstrap;

import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;

/** 从可信配置解析 Client 外部 Origin 与 SSO URL。 */
final class SsoClientUrlSupport {

    private SsoClientUrlSupport() {
        throw new IllegalStateException("Utility class");
    }

    static URI externalOrigin(
            SsoClientProperties properties, SaSsoClientTemplate template) {
        URI configured = properties.getExternalOrigin();
        if (configured != null) {
            return origin(configured, "sz.sso-client.external-origin");
        }
        SaSsoClientConfig config = requiredConfig(template);
        return origin(httpUri(
                config.getCurrSsoLogin(),
                "sa-token.sso-client.curr-sso-login"),
                "sa-token.sso-client.curr-sso-login");
    }

    static URI callback(SaSsoClientTemplate template) {
        URI callback = httpUri(
                requiredConfig(template).getCurrSsoLogin(),
                "sa-token.sso-client.curr-sso-login");
        if (callback.getRawQuery() != null || callback.getRawFragment() != null) {
            throw new SsoClientConfigurationException(
                    "sa-token.sso-client.curr-sso-login 不得包含 query 或 fragment");
        }
        return callback;
    }

    static URI authUrl(SaSsoClientTemplate template) {
        return httpUri(
                requiredConfig(template).getAuthUrl(),
                "sa-token.sso-client.auth-url");
    }

    static String clientFlag(SaSsoClientTemplate template) {
        String client = template.getClient();
        if (client == null || client.isBlank()) {
            throw new SsoClientConfigurationException(
                    "sa-token.sso-client.client 不能为空");
        }
        return client;
    }

    static boolean localLike(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "local".equals(profile)
                        || "dev".equals(profile)
                        || "test".equals(profile));
    }

    static boolean allowMissingBrowserSource(
            SsoClientProperties properties, Environment environment) {
        Boolean configured = properties.getAllowMissingBrowserSource();
        return configured == null ? localLike(environment) : configured;
    }

    static boolean secureCookie(
            SsoClientProperties properties,
            URI externalOrigin,
            Environment environment) {
        Boolean configured = properties.getSecureCookie();
        boolean secure = configured == null
                ? "https".equalsIgnoreCase(externalOrigin.getScheme())
                : configured;
        if (!localLike(environment) && !secure) {
            throw new SsoClientConfigurationException(
                    "非 local/dev/test 环境的 SZ_SSO_BROWSER Cookie 必须启用 Secure");
        }
        return secure;
    }

    private static SaSsoClientConfig requiredConfig(SaSsoClientTemplate template) {
        if (template == null || template.getClientConfig() == null) {
            throw new SsoClientConfigurationException("Sa-Token SSO Client 配置不可用");
        }
        return template.getClientConfig();
    }

    private static URI origin(URI uri, String field) {
        URI http = validateHttp(uri, field);
        try {
            return new URI(
                    http.getScheme(),
                    null,
                    http.getHost(),
                    http.getPort(),
                    null,
                    null,
                    null);
        } catch (URISyntaxException exception) {
            throw new SsoClientConfigurationException(field + " 不是合法 HTTP Origin");
        }
    }

    private static URI httpUri(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SsoClientConfigurationException(field + " 不能为空");
        }
        try {
            return validateHttp(new URI(value), field);
        } catch (URISyntaxException exception) {
            throw new SsoClientConfigurationException(field + " 不是合法 URL");
        }
    }

    private static URI validateHttp(URI uri, String field) {
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new SsoClientConfigurationException(field + " 必须是绝对 HTTP(S) URL");
        }
        return uri;
    }
}
