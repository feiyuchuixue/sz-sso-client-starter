package com.sz.ssoclient.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import com.sz.ssoclient.message.handler.SsoClientSuperAdminBatchSyncHandler;
import com.sz.ssoclient.message.handler.SsoClientSuperAdminSyncHandler;
import com.sz.ssoclient.message.SsoMessageSender;
import com.sz.ssoclient.message.SsoMessageInterceptor;
import com.sz.ssoclient.message.SsoServerMessageDispatcher;
import com.sz.ssoclient.message.SsoServerMessageHandler;
import com.sz.ssoclient.message.handler.SsoRegisterMessageHandler;
import com.sz.ssoclient.spi.SsoRoleBindingService;
import com.sz.ssoclient.spi.SsoUserMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * SSO Client 消息分发自动配置.
 */
@Slf4j
@AutoConfiguration(after = SsoClientTemplateAutoConfiguration.class)
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean(SsoUserMappingService.class)
public class SsoClientMessageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoMessageSender.class)
    public SsoMessageSender ssoMessageSender() {
        log.info("[SSO] 自动配置: 注册 SsoMessageSender");
        return new SsoMessageSender();
    }

    @Bean
    @ConditionalOnMissingBean(SsoRegisterMessageHandler.class)
    public SsoRegisterMessageHandler ssoRegisterMessageHandler(SsoUserMappingService ssoUserMappingService) {
        return new SsoRegisterMessageHandler(ssoUserMappingService);
    }

    @Bean
    @ConditionalOnMissingBean(SsoClientSuperAdminSyncHandler.class)
    @ConditionalOnBean(SsoRoleBindingService.class)
    public SsoClientSuperAdminSyncHandler ssoClientSuperAdminSyncHandler(SsoUserMappingService ssoUserMappingService,
                                                                         SsoRoleBindingService ssoRoleBindingService) {
        log.info("[SSO] 自动配置: 注册 Server -> Client 超管同步处理器");
        return new SsoClientSuperAdminSyncHandler(ssoUserMappingService, ssoRoleBindingService);
    }


    @Bean
    @ConditionalOnMissingBean(SsoClientSuperAdminBatchSyncHandler.class)
    @ConditionalOnBean(SsoRoleBindingService.class)
    public SsoClientSuperAdminBatchSyncHandler ssoClientSuperAdminBatchSyncHandler(SsoUserMappingService ssoUserMappingService,
                                                                              SsoRoleBindingService ssoRoleBindingService) {
        log.info("[SSO] 自动配置: 注册 Server -> Client 批量超管同步处理器");
        return new SsoClientSuperAdminBatchSyncHandler(ssoUserMappingService, ssoRoleBindingService);
    }
    @Bean
    @ConditionalOnMissingBean
    public SsoServerMessageDispatcher ssoServerMessageDispatcher(ObjectProvider<SsoServerMessageHandler> handlers,
                                                                 ObjectProvider<SsoMessageInterceptor> interceptors) {
        List<SsoServerMessageHandler> handlerList = handlers.orderedStream().toList();
        List<SsoMessageInterceptor> interceptorList = interceptors.orderedStream().toList();
        return new SsoServerMessageDispatcher(handlerList, interceptorList);
    }

    @Bean
    public SmartInitializingSingleton ssoServerMessageHandlerRegistrar(SaSsoClientTemplate ssoClientTemplate,
                                                                       SsoServerMessageDispatcher dispatcher) {
        return () -> {
            dispatcher.handlers().forEach((messageType, handler) -> {
                ssoClientTemplate.messageHolder.addHandle(messageType, dispatcher::dispatch);
                log.info("[SSO] 注册消息处理器: type={}, handler={}", messageType, handler.getClass().getSimpleName());
            });
            if (dispatcher.handlers().isEmpty()) {
                log.debug("[SSO] 未检测到 SsoServerMessageHandler，跳过注册");
            }
        };
    }
}



