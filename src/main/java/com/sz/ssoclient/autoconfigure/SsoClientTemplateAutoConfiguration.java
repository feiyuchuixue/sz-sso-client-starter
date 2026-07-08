package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.dtflys.forest.Forest;
import com.dtflys.forest.http.ForestRequest;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Sa-Token SSO Client 模板策略配置.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoClientLoginAdapter.class})
@EnableConfigurationProperties(SsoClientMessageHttpProperties.class)
public class SsoClientTemplateAutoConfiguration {

    @Autowired
    public void configSsoTemplate(SaSsoClientTemplate ssoClientTemplate,
                                  SsoUserMappingService ssoUserMappingService,
                                  SsoClientMessageHttpProperties messageHttpProperties) {
        ssoClientTemplate.strategy.convertCenterIdToLoginId = centerId -> {
            Object clientUserId = ssoUserMappingService.resolveOrProvisionClientUser(centerId);
            log.info("[SSO] convertCenterIdToLoginId: centerId={} -> clientUserId={}", centerId, clientUserId);
            return clientUserId;
        };
        ssoClientTemplate.strategy.convertLoginIdToCenterId = ssoUserMappingService::toServerUserId;
        ssoClientTemplate.strategy.sendRequest = url -> buildSsoMessageRequest(url, messageHttpProperties).executeAsString();
        log.info("[SSO] 自动配置完成: SSO Client ID 转换策略与消息请求超时已注册, connectTimeoutMs={}, readTimeoutMs={}",
                messageHttpProperties.connectTimeoutMillis(), messageHttpProperties.readTimeoutMillis());
    }

    static ForestRequest<?> buildSsoMessageRequest(String url, SsoClientMessageHttpProperties properties) {
        return Forest.get(url)
                .setTimeout(properties.readTimeoutMillis())
                .connectTimeout(properties.connectTimeoutMillis())
                .readTimeout(properties.readTimeoutMillis());
    }
}