package com.sz.ssoclient.internal.login;

import com.sz.ssoclient.internal.messaging.SsoClientMessageGateway;
import com.sz.ssoclient.spi.SsoClientIdentityAdapter;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoDefaultAccessInitializer;
import com.sz.ssoclient.spi.SsoSuperAdminAuthorityAdapter;
import com.sz.ssoclient.spi.advanced.SsoClientLocalSessionAccessor;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 固定登录、JIT、超管和默认访问顺序的内部配置边界。 */
@Configuration(proxyBeanMethods = false)
public class SsoLoginConfiguration {

    @Bean
    public SsoTicketAuthenticator ssoTicketAuthenticator(SaSsoClientTemplate template) {
        return new SsoTicketAuthenticator(template);
    }

    @Bean
    public SsoClientIdentityResolver ssoClientIdentityResolver(
            SsoClientIdentityAdapter identityAdapter,
            SsoClientMessageGateway messageGateway) {
        return new SsoClientIdentityResolver(identityAdapter, messageGateway);
    }

    @Bean
    public SsoClientLoginOrchestrator<?> ssoClientLoginOrchestrator(
            SsoTicketAuthenticator ticketAuthenticator,
            SsoClientIdentityResolver identityResolver,
            SsoClientMessageGateway messageGateway,
            SsoClientIdentityAdapter identityAdapter,
            SsoClientLoginAdapter<?> loginAdapter,
            ObjectProvider<SsoDefaultAccessInitializer> defaultAccessInitializer,
            ObjectProvider<SsoSuperAdminAuthorityAdapter> superAdminAuthorityAdapter,
            SsoClientLocalSessionAccessor localSessionAccessor) {
        return new SsoClientLoginOrchestrator<>(
                ticketAuthenticator,
                identityResolver,
                messageGateway,
                identityAdapter,
                loginAdapter,
                defaultAccessInitializer.getIfAvailable(),
                superAdminAuthorityAdapter.getIfAvailable(),
                localSessionAccessor);
    }
}
