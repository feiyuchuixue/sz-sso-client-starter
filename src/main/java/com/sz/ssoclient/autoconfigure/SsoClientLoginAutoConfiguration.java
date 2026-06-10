package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.sync.DefaultSsoRoleBindingService;
import com.sz.ssoclient.login.LoginStep;
import com.sz.ssoclient.login.SsoLoginOrchestrator;
import com.sz.ssoclient.login.step.ApplyDefaultRoleStep;
import com.sz.ssoclient.login.step.ApplySuperAdminStep;
import com.sz.ssoclient.login.step.BuildLoginUserStep;
import com.sz.ssoclient.login.step.CreateLoginResultStep;
import com.sz.ssoclient.login.step.PrepareLoginParameterStep;
import com.sz.ssoclient.login.step.QuerySuperAdminStep;
import com.sz.ssoclient.login.step.WriteTokenSessionStep;
import com.sz.ssoclient.service.SsoClientService;
import com.sz.ssoclient.service.impl.SsoClientServiceImpl;
import com.sz.ssoclient.spi.SsoClientLoginAdapter;
import com.sz.ssoclient.spi.SsoClientRoleProvider;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * SSO Client 登录编排自动配置.
 */
@Slf4j
@AutoConfiguration(after = SsoClientTemplateAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoClientLoginAdapter.class})
public class SsoClientLoginAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoRoleBindingService.class)
    public DefaultSsoRoleBindingService defaultSsoRoleBindingService() {
        log.info("[SSO] 自动配置: 注册 DefaultSsoRoleBindingService");
        return new DefaultSsoRoleBindingService();
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SsoLoginOrchestrator<?> ssoLoginOrchestrator(SsoClientLoginAdapter<?> loginAdapter,
                                                        SsoUserMappingService userMappingService,
                                                        @Nullable SsoClientRoleProvider roleProvider,
                                                        @Nullable SsoRoleBindingService roleBindingService) {
        List<LoginStep<Object>> steps = List.of(
                new PrepareLoginParameterStep<>(),
                new ApplyDefaultRoleStep<>(roleProvider, roleBindingService),
                new QuerySuperAdminStep<>(userMappingService),
                new ApplySuperAdminStep<>(roleBindingService),
                new BuildLoginUserStep((SsoClientLoginAdapter) loginAdapter),
                new CreateLoginResultStep((SsoClientLoginAdapter) loginAdapter),
                new WriteTokenSessionStep<>()
        );
        return new SsoLoginOrchestrator<>(steps);
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SsoClientService ssoClientService(SsoLoginOrchestrator<?> loginOrchestrator) {
        log.info("[SSO] 自动配置: 注册 SsoClientService");
        return new SsoClientServiceImpl((SsoLoginOrchestrator) loginOrchestrator);
    }
}

