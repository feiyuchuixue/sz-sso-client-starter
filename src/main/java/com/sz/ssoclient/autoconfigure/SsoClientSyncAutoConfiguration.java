package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.sync.SsoSyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * SSO Client 同步工具自动配置.
 */
@Slf4j
@AutoConfiguration(before = SsoClientAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
public class SsoClientSyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoSyncHelper.class)
    public SsoSyncHelper ssoSyncHelper() {
        log.info("[SSO] 自动配置: 注册 SsoSyncHelper");
        return new SsoSyncHelper();
    }
}

