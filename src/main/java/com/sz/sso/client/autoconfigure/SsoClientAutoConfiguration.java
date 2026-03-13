package com.sz.sso.client.autoconfigure;

import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.util.SaResult;
import com.sz.sso.client.DefaultSsoRoleBindingService;
import com.sz.sso.client.DefaultSsoSessionCreator;
import com.sz.sso.client.SsoClientRoleProvider;
import com.sz.sso.client.SsoLoginHandler;
import com.sz.sso.client.SsoMessageSender;
import com.sz.sso.client.SsoRoleBindingService;
import com.sz.sso.client.SsoServerMessageHandler;
import com.sz.sso.client.SsoSessionCreator;
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

import java.util.List;

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
 *   <li>注册内置消息处理器（{@code REGISTER} 消息 → 用户同步）</li>
 *   <li>扫描并注册业务方提供的 {@link SsoServerMessageHandler} 实现（可选，支持多个）</li>
 *   <li>注册 {@link SsoSessionCreator} 默认实现（若业务方未提供）</li>
 *   <li>注册 {@link SsoRoleBindingService} 默认实现（若业务方未提供）</li>
 *   <li>{@code SsoSyncHelper} Bean 由 {@link SsoSyncHelperAutoConfiguration} 更早注册，避免循环依赖</li>
 *   <li>注册 {@link SsoMessageSender} Bean（提供向 Server 发送消息的通用能力）</li>
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
            String client = message.get("client").toString();
            log.info("[SSO] 收到 REGISTER 消息, ssoUserId={}", ssoUserId);
            ssoUserMappingService.syncSsoRegisterUser(message, client);
            return SaResult.ok();
        });

        log.info("[SSO] 自动配置完成: 策略函数和消息处理器已注册");
    }

    /**
     * 扫描并批量注册业务方提供的 {@link SsoServerMessageHandler} 实现.
     * <p>
     * 容器中所有实现了 {@link SsoServerMessageHandler} 接口的 Bean 都会被自动发现并注册到
     * {@code SaSsoClientTemplate#messageHolder}，无需手动调用 {@code addHandle}。
     * 若未提供任何实现，则跳过，不影响正常功能。
     * </p>
     *
     * @param ssoClientTemplate sa-token SSO Client 模板
     * @param customHandlers    业务方注册的自定义消息处理器列表（可为空）
     */
    @Autowired(required = false)
    public void configCustomMessageHandlers(SaSsoClientTemplate ssoClientTemplate,
                                            @Nullable List<SsoServerMessageHandler> customHandlers) {
        if (customHandlers == null || customHandlers.isEmpty()) {
            log.debug("[SSO] 未检测到自定义 SsoServerMessageHandler，跳过注册");
            return;
        }
        for (SsoServerMessageHandler handler : customHandlers) {
            ssoClientTemplate.messageHolder.addHandle(
                    handler.messageType(),
                    handler::handle
            );
            log.info("[SSO] 注册自定义消息处理器: type={}, handler={}",
                    handler.messageType(), handler.getClass().getSimpleName());
        }
    }

    /**
     * SsoMessageSender Bean：提供向 SSO Server 发送自定义消息的通用能力.
     * <p>
     * 支持同步（{@link SsoMessageSender#sendToServer}）和
     * 异步（{@link SsoMessageSender#sendToServerAsync}）两种发送方式，
     * 并自动填充 {@code clientId}。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(SsoMessageSender.class)
    public SsoMessageSender ssoMessageSender() {
        log.info("[SSO] 自动配置: 注册 SsoMessageSender");
        return new SsoMessageSender();
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
