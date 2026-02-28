package com.sz.sso.client.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.sso.client.DefaultSsoRoleBindingService;
import com.sz.sso.client.DefaultSsoSessionCreator;
import com.sz.sso.client.SsoClientRoleProvider;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoRoleBindingService;
import com.sz.sso.client.SsoSessionCreator;
import com.sz.sso.client.SsoSyncHelper;
import com.sz.sso.client.SsoUserMappingService;
import com.sz.sso.client.controller.SsoClientController;
import com.sz.sso.client.service.SsoClientService;
import com.sz.sso.client.service.impl.SsoClientServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;

import static com.sz.sso.client.SsoCoreConstant.MESSAGE_REGISTER;

/**
 * SSO Client 自动配置.
 * <p>
 * 当 classpath 中存在 {@link SaSsoClientTemplate} 且业务方提供了
 * {@link SsoUserMappingService} 和 {@link SsoLoginHandler} 的实现 Bean 时自动激活。
 * </p>
 *
 * <h3>自动完成以下配置</h3>
 * <ul>
 *   <li>注册 {@link SaSsoClientTemplate} 的 centerId/loginId 转换策略</li>
 *   <li>注册消息处理器（{@code REGISTER} 消息 → 用户同步）</li>
 *   <li>注册 {@link SsoSessionCreator} 默认实现（若业务方未提供）</li>
 *   <li>注册 {@link SsoRoleBindingService} 默认实现（若业务方未提供）</li>
 *   <li>注册 {@link SsoSyncHelper} Bean（提供超管状态同步能力）</li>
 *   <li>注册 {@link SsoClientService} Bean</li>
 *   <li>导入 {@link SsoClientController} 提供标准 SSO 端点</li>
 * </ul>
 *
 * @author sz
 * @version 2.0
 * @since 2025/6/20
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(SaSsoClientTemplate.class)
@ConditionalOnBean({SsoUserMappingService.class, SsoLoginHandler.class})
@Import(SsoClientController.class)
public class SsoClientAutoConfiguration {

    /**
     * 默认 SsoSessionCreator：基于 Sa-Token 原生 API 建立会话.
     */
    @Bean
    @ConditionalOnMissingBean(SsoSessionCreator.class)
    public SsoSessionCreator<?> defaultSsoSessionCreator() {
        log.info("[SSO] 自动配置: 注册 DefaultSsoSessionCreator");
        return new DefaultSsoSessionCreator();
    }

    /**
     * 默认 SsoRoleBindingService：首次登录默认角色初始化时打印 warn 日志.
     * <p>
     * 若业务方已注册自己的 {@link SsoRoleBindingService} Bean，本 Bean 不会创建。
     * 若业务方未提供 {@link SsoClientRoleProvider}，此 Bean 注册但不会被调用。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(SsoRoleBindingService.class)
    public SsoRoleBindingService defaultSsoRoleBindingService() {
        log.info("[SSO] 自动配置: 注册 DefaultSsoRoleBindingService（仅 warn 日志）");
        return new DefaultSsoRoleBindingService();
    }

    /**
     * SsoSyncHelper Bean：提供超管状态异步同步到 Server 的能力.
     * <p>
     * Client 内部超管角色变更（赋予/撤销）后调用
     * {@link SsoSyncHelper#syncSuperAdmin(Object, boolean)} 通知 Server 同步。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(SsoSyncHelper.class)
    public SsoSyncHelper ssoSyncHelper(SsoUserMappingService ssoUserMappingService) {
        log.info("[SSO] 自动配置: 注册 SsoSyncHelper");
        return new SsoSyncHelper(ssoUserMappingService);
    }

    /**
     * 配置 SaSsoClientTemplate 的策略函数和消息处理器.
     */
    @Autowired
    public void configSsoTemplate(SaSsoClientTemplate ssoClientTemplate,
                                   SsoUserMappingService ssoUserMappingService) {
        // centerId → localUserId
        ssoClientTemplate.strategy.convertCenterIdToLoginId = (centerId) -> {
            log.info("[SSO] convertCenterIdToLoginId: centerId={}", centerId);
            Object clientUserId = ssoUserMappingService.toClientUserId(centerId);
            log.info("[SSO] convertCenterIdToLoginId: centerId={} → clientUserId={}", centerId, clientUserId);
            return clientUserId;
        };
        // localUserId → centerId
        ssoClientTemplate.strategy.convertLoginIdToCenterId = (loginId) -> {
            log.debug("[SSO] convertLoginIdToCenterId: loginId={}", loginId);
            return ssoUserMappingService.toServerUserId(loginId);
        };

        // REGISTER 消息：Server 有新用户注册时推送
        ssoClientTemplate.messageHolder.addHandle(MESSAGE_REGISTER, (ssoTemplate, message) -> {
            Object ssoUserId = message.get("ssoUserId");
            log.info("[SSO] 收到 REGISTER 消息, ssoUserId={}", ssoUserId);
            ssoUserMappingService.syncSsoRegisterUser(message);
            return SaResult.ok();
        });

        log.info("[SSO] 自动配置完成: 策略函数和消息处理器已注册");
    }

    /**
     * 注册 SsoClientService Bean.
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public SsoClientService ssoClientService(SsoLoginHandler<?> ssoLoginHandler,
                                              SsoSessionCreator<?> ssoSessionCreator,
                                              SsoUserMappingService ssoUserMappingService,
                                              @Nullable SsoClientRoleProvider ssoClientRoleProvider,
                                              @Nullable SsoRoleBindingService ssoRoleBindingService) {
        log.info("[SSO] 自动配置: 注册 SsoClientService, 首次登录默认角色={}",
                 ssoClientRoleProvider != null ? "启用（defaultRoleKey=" + ssoClientRoleProvider.getDefaultRoleKey() + "）" : "跳过");
        return new SsoClientServiceImpl(ssoLoginHandler, ssoSessionCreator, ssoUserMappingService,
                                        ssoClientRoleProvider, ssoRoleBindingService);
    }

}
