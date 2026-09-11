package com.sz.ssoclient.internal.bootstrap;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.internal.login.SsoClientLoginOrchestrator;
import com.sz.ssoclient.internal.login.transaction.SsoLoginTransactionService;
import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.internal.web.SsoClientBrowserBinding;
import com.sz.ssoclient.internal.web.SsoClientWebController;
import com.sz.ssoclient.internal.web.SsoClientWebService;
import com.sz.ssoclient.internal.web.SsoSameOriginGuard;
import com.sz.ssoclient.internal.web.SsoStrictJsonGuard;
import com.sz.ssoclient.internal.web.SsoWebConfiguration;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 六条 Browser POST 与 URL 工厂的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
@Import(SsoWebConfiguration.class)
public class SsoClientEndpointConfiguration {

    @Bean
    public SsoSameOriginGuard ssoSameOriginGuard(
            SsoClientProperties properties,
            SaSsoClientTemplate template,
            Environment environment) {
        return new SsoSameOriginGuard(
                SsoClientUrlSupport.externalOrigin(properties, template),
                SsoClientUrlSupport.allowMissingBrowserSource(properties, environment));
    }

    @Bean
    public SsoClientWebService.LoginAuthorizationUrlFactory ssoLoginAuthorizationUrlFactory(
            SaSsoClientTemplate template) {
        return (state, theme) -> buildAuthorizationUrl(template, state, theme);
    }

    @Bean
    public SsoClientWebService.PortalUrlFactory ssoPortalUrlFactory(
            SaSsoClientTemplate template,
            SsoClientProperties properties) {
        URI authUrl = SsoClientUrlSupport.authUrl(template);
        String portalPath = validatePortalPath(properties.getPortalLoginPath());
        return (ticket, target, targetPath) -> buildPortalUrl(
                authUrl, portalPath, ticket, targetPath);
    }

    @Bean
    public SsoClientWebService ssoClientWebService(
            SsoLoginTransactionService transactions,
            SsoClientLoginOrchestrator<?> loginOrchestrator,
            SsoClientMessageGateway messageGateway,
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLocalSessionAccessor localSessions,
            SsoClientWebService.LoginAuthorizationUrlFactory loginUrls,
            SsoClientWebService.PortalUrlFactory portalUrls) {
        return new SsoClientWebService(
                transactions,
                loginOrchestrator,
                messageGateway,
                identityAdapter,
                localSessions,
                loginUrls,
                portalUrls);
    }

    @Bean
    public SsoClientWebController ssoClientWebController(
            SsoClientWebService webService,
            SsoClientBrowserBinding browserBinding,
            SsoSameOriginGuard sameOriginGuard,
            SsoStrictJsonGuard strictJsonGuard) {
        return new SsoClientWebController(
                webService,
                browserBinding,
                sameOriginGuard,
                strictJsonGuard);
    }

    private static String buildAuthorizationUrl(
            SaSsoClientTemplate template, String state, String theme) {
        requireText(state, "state");
        String callback = appendQuery(
                SsoClientUrlSupport.callback(template).toASCIIString(),
                Map.of("state", state));
        String authorizationUrl = requireText(
                template.buildServerAuthUrl(callback, null),
                "authorizationUrl");
        authorizationUrl = encodeRawRedirectValue(authorizationUrl, callback);
        Map<String, String> publicParameters = new LinkedHashMap<>();
        publicParameters.put("state", state);
        if (theme != null && !theme.isBlank()) {
            publicParameters.put("theme", theme);
        }
        return appendQuery(authorizationUrl, publicParameters);
    }

    private static String buildPortalUrl(
            URI authUrl,
            String portalPath,
            String ticket,
            String targetPath) {
        requireText(ticket, "portal ticket");
        requireText(targetPath, "targetPath");
        try {
            URI portal = new URI(
                    authUrl.getScheme(),
                    null,
                    authUrl.getHost(),
                    authUrl.getPort(),
                    portalPath,
                    null,
                    null);
            return appendQuery(
                    portal.toASCIIString(),
                    Map.of("ticket", ticket, "_back", targetPath));
        } catch (URISyntaxException exception) {
            throw new SsoClientConfigurationException("无法构造 SSO Portal URL");
        }
    }

    private static String validatePortalPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")
                || path.startsWith("//") || path.indexOf('\\') >= 0
                || path.indexOf('?') >= 0 || path.indexOf('#') >= 0
                || path.indexOf(':') >= 0) {
            throw new SsoClientConfigurationException(
                    "sz.sso-client.portal-login-path 必须是安全绝对路径");
        }
        return path;
    }

    private static String appendQuery(String baseUrl, Map<String, String> parameters) {
        StringBuilder result = new StringBuilder(baseUrl);
        result.append(baseUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!first) {
                result.append('&');
            }
            first = false;
            result.append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(entry.getValue()));
        }
        return result.toString();
    }

    private static String encodeRawRedirectValue(
            String authorizationUrl, String callback) {
        String encodedCallback = encode(callback);
        if (authorizationUrl.contains(encodedCallback)) {
            return authorizationUrl;
        }
        int callbackIndex = authorizationUrl.indexOf(callback);
        if (callbackIndex <= 0 || authorizationUrl.charAt(callbackIndex - 1) != '=') {
            throw new IllegalStateException(
                    "Sa-Token authorization URL 未包含预期 redirect callback");
        }
        return authorizationUrl.substring(0, callbackIndex)
                + encode(authorizationUrl.substring(callbackIndex));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " 不能为空");
        }
        return value;
    }
}
