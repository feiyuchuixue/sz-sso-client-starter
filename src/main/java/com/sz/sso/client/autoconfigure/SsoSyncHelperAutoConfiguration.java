package com.sz.sso.client.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.sso.client.SsoSyncHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * SsoSyncHelper 自动配置.
 * <p>
 * 独立于 {@link SsoClientAutoConfiguration} 之外注册 {@link SsoSyncHelper}，
 * 避免与业务方的 {@code SsoUserMappingService} 实现产生循环依赖。
 * </p>
 * <p>
 * {@link SsoSyncHelper} 本身不依赖任何 Spring Bean，可在容器启动早期安全注册，
 * 业务方的 Service 类可通过构造注入直接使用。
 * </p>
 *
 * @author sz
 * @version 1.0
 * @since 2025/6/23
 */
@Slf4j
@AutoConfiguration(before = SsoClientAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
public class SsoSyncHelperAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoSyncHelper.class)
    public SsoSyncHelper ssoSyncHelper() {
        log.info("[SSO] 自动配置: 注册 SsoSyncHelper");
        return new SsoSyncHelper();
    }

}
