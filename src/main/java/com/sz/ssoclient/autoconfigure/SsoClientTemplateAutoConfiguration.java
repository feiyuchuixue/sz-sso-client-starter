package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Sa-Token SSO Client 模板策略配置.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoClientLoginAdapter.class})
public class SsoClientTemplateAutoConfiguration {

    @Autowired
    public void configSsoTemplate(SaSsoClientTemplate ssoClientTemplate,
                                  SsoUserMappingService ssoUserMappingService) {
        ssoClientTemplate.strategy.convertCenterIdToLoginId = centerId -> {
            Object clientUserId = ssoUserMappingService.toClientUserId(centerId);
            log.info("[SSO] convertCenterIdToLoginId: centerId={} -> clientUserId={}", centerId, clientUserId);
            return clientUserId;
        };
        ssoClientTemplate.strategy.convertLoginIdToCenterId = ssoUserMappingService::toServerUserId;
        log.info("[SSO] 自动配置完成: SSO Client ID 转换策略已注册");
    }
}

