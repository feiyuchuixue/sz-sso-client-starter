package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.internal.bootstrap.SsoClientCoreConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientEndpointConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientInfrastructureConfiguration;
import com.sz.ssoclient.internal.bootstrap.SsoClientProperties;
import com.sz.ssoclient.internal.bootstrap.SsoClientSpiConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** Starter 唯一公开自动配置入口。 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "cn.dev33.satoken.spring.sso.SaSsoBeanRegister",
        "cn.dev33.satoken.spring.sso.SaSsoBeanInject"
})
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnProperty(
        prefix = "sz.sso-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(SsoClientProperties.class)
@Import({
        SsoClientCoreConfiguration.class,
        SsoClientSpiConfiguration.class,
        SsoClientInfrastructureConfiguration.class,
        SsoClientEndpointConfiguration.class
})
public class SsoClientAutoConfiguration {
}