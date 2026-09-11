package com.sz.ssoclient.internal.bootstrap;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.dtflys.forest.Forest;
import com.sz.ssoclient.internal.login.SsoLoginConfiguration;
import com.sz.ssoclient.internal.messaging.SsoMessagingConfiguration;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Sa-Token transport、固定消息与固定登录编排的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
@Import({
        SsoSaTokenClientCompatibilityConfiguration.class,
        SsoMessagingConfiguration.class,
        SsoLoginConfiguration.class
})
public class SsoClientCoreConfiguration {

    @Bean
    public SmartInitializingSingleton ssoClientTemplateStrategyInitializer(
            SaSsoClientTemplate template,
            SsoClientIdentityAdapter identityAdapter,
            SsoClientProperties properties) {
        return () -> {
            validateTimeouts(properties);
            template.strategy.convertCenterIdToLoginId = centerId -> identityAdapter
                    .findLocalUserId(requiredLong(centerId))
                    .orElseThrow(() -> new IllegalStateException("SSO 用户不存在本地映射"));
            template.strategy.convertLoginIdToCenterId = loginId -> identityAdapter
                    .findSsoUserId(requiredText(loginId))
                    .orElse(null);
            template.strategy.sendRequest = url -> Forest.get(url)
                    .connectTimeout(properties.getMessageConnectTimeoutMillis())
                    .readTimeout(properties.getMessageReadTimeoutMillis())
                    .executeAsString();
        };
    }

    private static void validateTimeouts(SsoClientProperties properties) {
        if (properties.getMessageConnectTimeoutMillis() <= 0
                || properties.getMessageReadTimeoutMillis() <= 0) {
            throw new SsoClientConfigurationException(
                    "SSO message connect/read timeout 必须为正整数毫秒");
        }
    }

    private static long requiredLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("centerId 不能为空");
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("centerId 必须是整数", exception);
        }
    }

    private static String requiredText(Object value) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("localUserId 不能为空");
        }
        return value.toString();
    }
}
